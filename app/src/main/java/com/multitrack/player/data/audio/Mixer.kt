package com.multitrack.player.data.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.multitrack.player.data.audio.source.PcmSource
import com.multitrack.player.domain.model.AudioError
import com.multitrack.player.domain.model.MetronomeSubdivision
import com.multitrack.player.domain.model.TrackMeterLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.math.roundToLong

/**
 * Single-producer, single-consumer audio mixer.
 *
 * Architecture (see prompt):
 *
 *   [PcmSource] (already decoded to float stereo @ bus rate)
 *      │
 *      ▼
 *   ┌──────────────────────────┐
 *   │ Mixer pull loop (audio)  │  one thread, priority boosted
 *   │  for each source:        │
 *   │   read(stereo buffer)    │  (BPM step is baked into the source)
 *   │   mix into bus (gain)    │  (soft-clip at +6 dBFS)
 *   └─────────────┬────────────┘
 *                 ▼
 *           AudioTrack.write (float bus, MODE_STREAM)
 *
 * Because we share a *single* AudioTrack, drift between tracks is impossible
 * by construction: there is no per-track clock. The PlaybackClock is updated
 * atomically with each successful write().
 */
class Mixer(
    private val clock: PlaybackClock,
    private val errorSink: (AudioError) -> Unit,
    private val onPlaybackCompleted: () -> Unit = {},
    private val onCountInCompleted: () -> Unit = {}
) {

    private companion object {
        const val BEATS_PER_BAR = 4
        const val QUANTIZED_LOOP_BARS = 4
    }

    @Volatile private var audioTrack: AudioTrack? = null
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    private val _isCountingIn = MutableStateFlow(false)
    val isCountingIn: StateFlow<Boolean> = _isCountingIn.asStateFlow()

    /** "prepared" sources are kept in a swap-friendly list. */
    private val sources = mutableListOf<PcmSource>()
    private var sourceIds: List<String> = emptyList()

    /** Per-source gain in linear units. Default 1.0. */
    private val gains = CopyOnWriteArrayList<Float>()
    private val _meterLevels = MutableStateFlow<Map<String, TrackMeterLevel>>(emptyMap())
    val meterLevels: StateFlow<Map<String, TrackMeterLevel>> = _meterLevels.asStateFlow()

    /** Currently selected BPM. Used to communicate speed factor to sources. */
    @Volatile private var currentBpm: Int = 120
    private var originalBpm: Int = 120

    /** Loop flag. */
    @Volatile private var loop: Boolean = true
    @Volatile private var loopQuantized: Boolean = false

    /** Synthesized click is mixed exclusively into the left output channel. */
    @Volatile private var metronomeEnabled: Boolean = false
    @Volatile private var metronomeVolume: Float = 0.45f
    @Volatile private var metronomeSubdivision: MetronomeSubdivision = MetronomeSubdivision.QUARTER_NOTE
    @Volatile private var metronomeBeatPhase: Double = 0.0
    @Volatile private var metronomeClickAgeFrames: Int = -1
    @Volatile private var beatsInQuantizedLoop: Double = 0.0
    @Volatile private var quantizedCycleActive: Boolean = false
    @Volatile private var quantizedCycleInitialized: Boolean = false

    fun configure(
        sources: List<PcmSource>,
        currentBpm: Int,
        originalBpm: Int,
        loopQuantized: Boolean,
        trackIds: List<String>
    ) {
        // If previously running, stop first
        stop()
        clock.reset()
        this.sources.clear()
        this.sourceIds = trackIds.take(sources.size)
        this.gains.clear()
        this.sources.addAll(sources)
        repeat(sources.size) {
            gains.add(1.0f)
        }
        _meterLevels.value = sourceIds.associateWith { TrackMeterLevel.Silent }
        this.currentBpm = currentBpm
        this.originalBpm = originalBpm.coerceAtLeast(1)
        this.loopQuantized = loopQuantized
        resetLoopAndMetronomePhase()
    }

    fun setBpm(bpm: Int) {
        currentBpm = bpm.coerceIn(40, 300)
    }

    fun setOriginalBpm(bpm: Int) {
        originalBpm = bpm.coerceAtLeast(1)
    }

    fun setLoop(enabled: Boolean) {
        loop = enabled
    }

    fun setLoopQuantized(enabled: Boolean) {
        loopQuantized = enabled
    }

    fun setMetronomeEnabled(enabled: Boolean) {
        metronomeEnabled = enabled
    }

    fun setMetronomeVolume(volume: Float) {
        metronomeVolume = volume.coerceIn(0f, 1f)
    }

    fun setMetronomeSubdivision(subdivision: MetronomeSubdivision) {
        metronomeSubdivision = subdivision
    }

    fun setGain(index: Int, gain: Float) {
        if (index in gains.indices) gains[index] = gain
    }

    fun start(countInBeats: Int = 0) {
        if (_isRunning.value) return
        if (sources.isEmpty()) {
            errorSink(AudioError.PlaybackError("No hay pistas"))
            return
        }
        try {
            releaseAudioTrack()
            val minBuf = AudioTrack.getMinBufferSize(
                PlaybackClock.OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_FLOAT
            )
            // Use a generous buffer to absorb jitter. ~200ms at 44.1kHz stereo float.
            val bufBytes = maxOf(minBuf, PlaybackClock.OUTPUT_SAMPLE_RATE * 2 * 4 / 5)

            val outputTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(PlaybackClock.OUTPUT_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack = outputTrack

            clock.start()
            _isRunning.value = true
            _isCountingIn.value = countInBeats > 0
            outputTrack.play()

            job = scope.launch { runMixerLoop(outputTrack, countInBeats.coerceIn(0, 4)) }
        } catch (t: Throwable) {
            errorSink(AudioError.PlaybackError(t.message))
            _isRunning.value = false
        }
    }

    fun stop() {
        _isRunning.value = false
        _isCountingIn.value = false
        job?.cancel()
        job = null
        releaseAudioTrack()
        clock.stop()
        _meterLevels.value = sourceIds.associateWith { TrackMeterLevel.Silent }
    }

    fun reset() {
        // Used by SeekToStart semantics: keep state, reset sources and clock.
        for (s in sources) s.seekToStart()
        resetLoopAndMetronomePhase()
        clock.reset()
    }

    fun release() {
        stop()
        for (s in sources) s.release()
        sources.clear()
        sourceIds = emptyList()
        gains.clear()
        _meterLevels.value = emptyMap()
    }

    /** Called by the engine when the ViewModel asks for a fresh play after stop. */
    fun softResetForRestart() {
        for (s in sources) s.seekToStart()
        resetLoopAndMetronomePhase()
        clock.reset()
    }

    private suspend fun runMixerLoop(outputTrack: AudioTrack, countInBeats: Int) =
        withContext(Dispatchers.Default) {
        val frameChunk = 1024 // ~23 ms @ 44.1 kHz; good trade-off latency / overhead
        val mixBuf = FloatArray(frameChunk * 2)
        val chunkBuf = FloatArray(frameChunk * 2)
        val sourceRms = FloatArray(sources.size)
        val sourcePeaks = FloatArray(sources.size)
        val smoothedRms = FloatArray(sources.size)
        val heldPeaks = FloatArray(sources.size)
        var lastPublishNanos = System.nanoTime()
        var completedNaturally = false

        if (!quantizedCycleInitialized) {
            quantizedCycleActive = loop && loopQuantized
            beatsInQuantizedLoop = 0.0
            metronomeBeatPhase = 0.0
            metronomeClickAgeFrames = if (metronomeEnabled) 0 else -1
            quantizedCycleInitialized = true
        }

        try {
            if (countInBeats > 0) {
                val bpmAtCountInStart = currentBpm.coerceAtLeast(1)
                val framesPerBeat = PlaybackClock.OUTPUT_SAMPLE_RATE * 60.0 / bpmAtCountInStart
                val totalCountInFrames = (framesPerBeat * countInBeats).roundToLong()
                    .coerceAtLeast(1L)
                val countInSubdivision = metronomeSubdivision
                val framesPerPulse = framesPerBeat / countInSubdivision.pulsesPerBeat
                val totalCountInPulses = countInBeats * countInSubdivision.pulsesPerBeat
                val clickFrames = PlaybackClock.OUTPUT_SAMPLE_RATE / 30
                var countInFramePosition = 0L
                var nextPulse = 0

                while (
                    countInFramePosition < totalCountInFrames &&
                        isActive &&
                        _isRunning.value &&
                        metronomeEnabled
                ) {
                    val framesToWrite = minOf(
                        frameChunk.toLong(),
                        totalCountInFrames - countInFramePosition
                    ).toInt()
                    val output = FloatArray(framesToWrite * 2)
                    for (frame in 0 until framesToWrite) {
                        val absoluteFrame = countInFramePosition + frame
                        if (nextPulse < totalCountInPulses &&
                            absoluteFrame >= (nextPulse * framesPerPulse).roundToLong()
                        ) {
                            val clickAgeFrames = absoluteFrame - (nextPulse * framesPerPulse).roundToLong()
                            if (clickAgeFrames < clickFrames) {
                                output[frame * 2] = clickSample(clickAgeFrames)
                            }
                            nextPulse++
                        } else {
                            val lastPulseStart = ((nextPulse - 1).coerceAtLeast(0) * framesPerPulse).roundToLong()
                            val clickAgeFrames = absoluteFrame - lastPulseStart
                            if (nextPulse > 0 && clickAgeFrames in 0 until clickFrames.toLong()) {
                                output[frame * 2] = clickSample(clickAgeFrames)
                            }
                        }
                    }
                    val written = outputTrack.write(
                        output,
                        0,
                        output.size,
                        AudioTrack.WRITE_BLOCKING
                    )
                    if (written <= 0) {
                        errorSink(AudioError.PlaybackError("No se pudo reproducir el count-in"))
                        _isRunning.value = false
                        break
                    }
                    countInFramePosition += written / 2
                }

                _isCountingIn.value = false
                if (_isRunning.value &&
                    (countInFramePosition >= totalCountInFrames || !metronomeEnabled)
                ) {
                    clock.reset()
                    metronomeBeatPhase = 0.0
                    metronomeClickAgeFrames = if (metronomeEnabled) 0 else -1
                    onCountInCompleted()
                }
            }

            while (isActive && _isRunning.value) {
                // Clear mix bus
                for (i in mixBuf.indices) mixBuf[i] = 0f
                sourceRms.fill(0f)
                sourcePeaks.fill(0f)

                var anyActive = false

                for ((idx, src) in sources.withIndex()) {
                    val written = src.read(chunkBuf, speedFactor = currentBpm.toDouble() / originalBpm.toDouble())
                    if (written > 0) {
                        anyActive = true
                        val g = gains[idx]
                        var i = 0
                        val end = written * 2
                        var energy = 0.0
                        var peak = 0f
                        while (i < end) {
                            val sample = chunkBuf[i] * g
                            mixBuf[i] += sample
                            energy += sample * sample
                            peak = max(peak, abs(sample))
                            i++
                        }
                        sourceRms[idx] = sqrt(energy / end.coerceAtLeast(1)).toFloat()
                        sourcePeaks[idx] = peak
                    }
                }

                updateTrackMeters(sourceRms, sourcePeaks, smoothedRms, heldPeaks, frameChunk)

                val shouldWriteSilenceForQuantizedLoop = loop && quantizedCycleActive
                val shouldRender = anyActive || shouldWriteSilenceForQuantizedLoop
                var renderedFrames = frameChunk
                var quantizedLoopBoundaryReached = false

                if (shouldRender) {
                    val clickFrames = PlaybackClock.OUTPUT_SAMPLE_RATE / 30
                    for (frame in 0 until frameChunk) {
                        if (!metronomeEnabled) {
                            metronomeClickAgeFrames = -1
                        } else if (metronomeClickAgeFrames in 0 until clickFrames) {
                            mixBuf[frame * 2] += clickSample(metronomeClickAgeFrames.toLong())
                            metronomeClickAgeFrames++
                            if (metronomeClickAgeFrames >= clickFrames) metronomeClickAgeFrames = -1
                        }

                        val beatIncrement = currentBpm.coerceAtLeast(1).toDouble() /
                            (60.0 * PlaybackClock.OUTPUT_SAMPLE_RATE)
                        metronomeBeatPhase += beatIncrement * metronomeSubdivision.pulsesPerBeat
                        if (metronomeBeatPhase >= 1.0) {
                            metronomeBeatPhase -= 1.0
                            metronomeClickAgeFrames = if (metronomeEnabled) 0 else -1
                        }

                        if (shouldWriteSilenceForQuantizedLoop) {
                            beatsInQuantizedLoop += beatIncrement
                            if (beatsInQuantizedLoop >= BEATS_PER_BAR * QUANTIZED_LOOP_BARS) {
                                renderedFrames = frame + 1
                                quantizedLoopBoundaryReached = true
                                break
                            }
                        }
                    }
                }

                if (renderedFrames < frameChunk) {
                    for (i in renderedFrames * 2 until mixBuf.size) mixBuf[i] = 0f
                }

                // Soft-clip + write. In quantized mode, write silence after a
                // short source ends so the entire four-bar cycle is preserved.
                if (shouldRender) {
                    // Soft-clip tanh to avoid hard clipping on summed signals.
                    var i = 0
                    val len = renderedFrames * 2
                    while (i < len) {
                        val l = mixBuf[i]
                        val r = mixBuf[i + 1]
                        mixBuf[i] = softClip(l)
                        mixBuf[i + 1] = softClip(r)
                        i += 2
                    }
                    val written = outputTrack.write(mixBuf, 0, len, AudioTrack.WRITE_BLOCKING)
                    if (written > 0) {
                        clock.onFramesProduced(written / 2)
                    }
                }

                if (quantizedLoopBoundaryReached) {
                    if (loop) {
                        sources.forEach { it.seekToStart() }
                        clock.reset()
                        beatsInQuantizedLoop = 0.0
                        metronomeBeatPhase = 0.0
                        metronomeClickAgeFrames = if (metronomeEnabled) 0 else -1
                        quantizedCycleActive = loopQuantized
                        quantizedCycleInitialized = true
                    } else {
                        _isRunning.value = false
                        completedNaturally = true
                        break
                    }
                } else if (!anyActive && !shouldWriteSilenceForQuantizedLoop) {
                    // Natural loop mode restarts when every source reaches EOF.
                    if (loop) {
                        sources.forEach { it.seekToStart() }
                        clock.reset()
                        beatsInQuantizedLoop = 0.0
                        quantizedCycleActive = loopQuantized
                        quantizedCycleInitialized = true
                        if (quantizedCycleActive) {
                            metronomeBeatPhase = 0.0
                            metronomeClickAgeFrames = if (metronomeEnabled) 0 else -1
                        }
                    } else {
                        _isRunning.value = false
                        completedNaturally = true
                        break
                    }
                }

                // Publish position to StateFlow at ~30 Hz max
                val now = System.nanoTime()
                if (now - lastPublishNanos > 33_000_000L) {
                    clock.publish()
                    _meterLevels.value = sourceIds.mapIndexedNotNull { index, id ->
                        if (index in smoothedRms.indices && index in heldPeaks.indices) {
                            id to TrackMeterLevel(smoothedRms[index], heldPeaks[index])
                        } else null
                    }.toMap()
                    lastPublishNanos = now
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            errorSink(AudioError.PlaybackError(t.message))
            _isRunning.value = false
        } finally {
            if (!_isRunning.value) {
                _isCountingIn.value = false
                releaseAudioTrack(outputTrack)
                clock.stop()
                _meterLevels.value = sourceIds.associateWith { TrackMeterLevel.Silent }
                if (completedNaturally) onPlaybackCompleted()
            } else {
                clock.publish()
            }
        }
    }

    /** tanh-based soft clip; gain ~1 below -6 dBFS, asymptotic above. */
    private fun softClip(sample: Float): Float {
        // Drive slightly so we still get full volume but no hard clipping.
        return tanh(sample.toDouble()).toFloat()
    }

    private fun updateTrackMeters(
        rmsValues: FloatArray,
        peakValues: FloatArray,
        smoothedRms: FloatArray,
        heldPeaks: FloatArray,
        frames: Int
    ) {
        val elapsedSeconds = frames.toFloat() / PlaybackClock.OUTPUT_SAMPLE_RATE
        for (index in smoothedRms.indices) {
            val targetRms = amplitudeToMeterLevel(rmsValues.getOrElse(index) { 0f })
            val previousRms = smoothedRms[index]
            val timeConstant = if (targetRms > previousRms) 0.035f else 0.28f
            val alpha = 1f - exp(-elapsedSeconds / timeConstant)
            smoothedRms[index] = previousRms + (targetRms - previousRms) * alpha

            val targetPeak = amplitudeToMeterLevel(peakValues.getOrElse(index) { 0f })
            heldPeaks[index] = if (targetPeak >= heldPeaks[index]) {
                targetPeak
            } else {
                max(targetPeak, heldPeaks[index] - elapsedSeconds / 1.2f)
            }
        }
    }

    private fun amplitudeToMeterLevel(amplitude: Float): Float {
        if (amplitude <= 0f) return 0f
        val decibels = 20f * log10(amplitude)
        return ((decibels + 60f) / 60f).coerceIn(0f, 1f)
    }

    private fun clickSample(ageFrames: Long): Float {
        val seconds = ageFrames.toDouble() / PlaybackClock.OUTPUT_SAMPLE_RATE
        return (
            sin(2.0 * PI * 1_800.0 * seconds) *
                exp(-seconds * 90.0) *
                metronomeVolume
            ).toFloat()
    }

    @Synchronized
    private fun releaseAudioTrack(expected: AudioTrack? = null) {
        val output = audioTrack ?: return
        if (expected != null && output !== expected) return
        audioTrack = null
        try {
            output.pause()
            output.flush()
            output.stop()
        } catch (_: Throwable) {
            // The track may already have completed or been stopped externally.
        }
        output.release()
    }

    private fun resetLoopAndMetronomePhase() {
        metronomeBeatPhase = 0.0
        metronomeClickAgeFrames = if (metronomeEnabled) 0 else -1
        beatsInQuantizedLoop = 0.0
        quantizedCycleActive = false
        quantizedCycleInitialized = false
    }
}

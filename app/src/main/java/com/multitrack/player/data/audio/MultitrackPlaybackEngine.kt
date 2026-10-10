package com.multitrack.player.data.audio

import android.content.ContentResolver
import android.net.Uri
import com.multitrack.player.data.audio.source.Mp3PcmSource
import com.multitrack.player.data.audio.source.PcmSource
import com.multitrack.player.data.audio.source.WavPcmSource
import com.multitrack.player.domain.model.AudioError
import com.multitrack.player.domain.model.AudioFormat
import com.multitrack.player.domain.model.AudioTrack
import com.multitrack.player.domain.model.PlaybackSession
import com.multitrack.player.domain.model.PlaybackState
import com.multitrack.player.domain.repository.PlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

/**
 * Production implementation of [PlaybackEngine].
 *
 * Lifecycle:
 *   prepare(session)  -> decodes all files into PcmSources (off the audio thread)
 *   play / pause / stop / setLoop / setBpm
 *   release()        -> must be called from the Activity / ViewModel.onCleared
 *
 * The class is *single-instance per session*: you call prepare() to load a new
 * session, which internally releases the previous one.
 */
class MultitrackPlaybackEngine(
    private val contentResolver: ContentResolver
) : PlaybackEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val clock = PlaybackClock()
    private val mixer = Mixer(clock, ::onEngineError)

    private val _state = MutableStateFlow(
        PlaybackState(tracks = emptyList(), isLoading = true)
    )

    override val state: StateFlow<PlaybackState> = combine(
        _state, clock.state, mixer.meterLevels
    ) { st, clk, meterLevels ->
        st.copy(positionMs = clk.positionMs, trackMeterLevels = meterLevels)
    }.stateIn(scope, SharingStarted.Eagerly, _state.value)

    private var preparedTracks: List<AudioTrack> = emptyList()
    private var baseDurationMs: Long = 0L

    /** Tracks whether we are resuming from pause (true) or starting fresh (false). */
    @Volatile private var resume: Boolean = false

    override fun prepare(session: PlaybackSession) {
        // Stop anything in flight and free previous sources
        mixer.release()
        _state.value = _state.value.copy(
            error = null,
            isPlaying = false,
            isPaused = false,
            isLoading = true
        )

        scope.launch {
            try {
                val sources = withContext(Dispatchers.IO) {
                    session.tracks.map { track -> decodeToSource(track) }
                }
                val durationMs = computeMixDurationMs(sources)
                baseDurationMs = durationMs
                val originalBpm = session.originalBpm.coerceIn(
                    PlaybackState.MIN_BPM,
                    PlaybackState.MAX_BPM
                )
                val initialBpm = session.initialBpm.coerceIn(
                    PlaybackState.MIN_BPM,
                    PlaybackState.MAX_BPM
                )
                mixer.configure(
                    sources = sources,
                    currentBpm = initialBpm,
                    originalBpm = originalBpm,
                    trackIds = session.tracks.map { it.id }
                )
                mixer.setLoop(session.isLoopEnabled)
                mixer.setMetronomeEnabled(session.isMetronomeEnabled)
                val trackIds = session.tracks.map { it.id }.toSet()
                preparedTracks = session.tracks
                resume = false
                _state.update { state ->
                    state.copy(
                        tracks = session.tracks,
                        currentBpm = initialBpm,
                        originalBpm = originalBpm,
                        durationMs = durationAtBpm(originalBpm, initialBpm),
                        positionMs = 0L,
                        isPlaying = false,
                        isPaused = false,
                        isLoopEnabled = session.isLoopEnabled,
                        isMetronomeEnabled = session.isMetronomeEnabled,
                        isLoading = false,
                        mutedTrackIds = session.mutedTrackIds.intersect(trackIds),
                        soloTrackIds = session.soloTrackIds.intersect(trackIds),
                        trackVolumes = session.trackVolumes
                            .filterKeys { it in trackIds }
                            .mapValues { (_, volume) -> volume.coerceIn(0f, 1f) },
                        error = null
                    )
                }
                applyTrackGains(_state.value)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = AudioError.DecoderError(t.message)
                )
            }
        }
    }

    override fun play() {
        if (preparedTracks.isEmpty()) return
        if (!resume) {
            mixer.softResetForRestart()
        }
        mixer.start()
        resume = true
        _state.value = _state.value.copy(isPlaying = true, isPaused = false, error = null)
    }

    override fun pause() {
        mixer.stop()
        // Position is preserved; next play() resumes from where we were.
        _state.value = _state.value.copy(isPlaying = false, isPaused = true)
    }

    override fun stop() {
        mixer.stop()
        mixer.reset()
        resume = false
        _state.value = _state.value.copy(isPlaying = false, isPaused = false, positionMs = 0L)
    }

    override fun seekToStart() {
        mixer.reset()
        // If we were paused mid-track, stay paused. Next play() starts from 0.
        resume = false
        _state.value = _state.value.copy(positionMs = 0L)
    }

    override fun setLoop(enabled: Boolean) {
        mixer.setLoop(enabled)
        _state.value = _state.value.copy(isLoopEnabled = enabled)
    }

    override fun setTrackMuted(trackId: String, muted: Boolean) {
        if (preparedTracks.none { it.id == trackId }) return
        _state.update { state ->
            state.copy(
                mutedTrackIds = if (muted) {
                    state.mutedTrackIds + trackId
                } else {
                    state.mutedTrackIds - trackId
                }
            )
        }
        applyTrackGains(_state.value)
    }

    override fun setMetronomeEnabled(enabled: Boolean) {
        mixer.setMetronomeEnabled(enabled)
        _state.update { it.copy(isMetronomeEnabled = enabled) }
    }

    override fun setTrackVolume(trackId: String, volume: Float) {
        if (preparedTracks.none { it.id == trackId }) return
        _state.update { state ->
            state.copy(trackVolumes = state.trackVolumes + (trackId to volume.coerceIn(0f, 1f)))
        }
        applyTrackGains(_state.value)
    }

    override fun setTrackSolo(trackId: String, solo: Boolean) {
        if (preparedTracks.none { it.id == trackId }) return
        _state.update { state ->
            state.copy(
                soloTrackIds = if (solo) state.soloTrackIds + trackId else state.soloTrackIds - trackId
            )
        }
        applyTrackGains(_state.value)
    }

    override fun setBpm(bpm: Int) {
        val clamped = bpm.coerceIn(PlaybackState.MIN_BPM, PlaybackState.MAX_BPM)
        mixer.setBpm(clamped)
        val newDurationMs = durationAtBpm(_state.value.originalBpm, clamped)
        _state.update { it.copy(currentBpm = clamped, durationMs = newDurationMs) }
    }

    override fun release() {
        mixer.release()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private fun onEngineError(error: AudioError) {
        _state.value = _state.value.copy(isPlaying = false, isPaused = false, error = error)
    }

    private fun applyTrackGains(state: PlaybackState) {
        val hasSoloTracks = state.soloTrackIds.isNotEmpty()
        preparedTracks.forEachIndexed { index, track ->
            val isExcludedBySolo = hasSoloTracks && track.id !in state.soloTrackIds
            val isMuted = track.id in state.mutedTrackIds || isExcludedBySolo
            val gain = if (isMuted) 0f else state.trackVolumes[track.id] ?: 1f
            mixer.setGain(index, gain)
        }
    }

    // ---------- helpers ----------

    private fun decodeToSource(track: AudioTrack): PcmSource {
        val uri = Uri.parse(track.uri)
        val stream = contentResolver.openInputStream(uri)
            ?: throw AudioError.FileNotFound
        return when (track.format) {
            AudioFormat.WAV -> WavPcmSource.fromStream(stream).getOrElse { t ->
                if (t is AudioError) throw t
                throw AudioError.DecoderError(t.message)
            }
            AudioFormat.MP3 -> Mp3PcmSource.fromStream(stream).getOrElse { t ->
                if (t is AudioError) throw t
                throw AudioError.DecoderError(t.message)
            }
        }
    }

    private fun computeMixDurationMs(sources: List<PcmSource>): Long {
        if (sources.isEmpty()) return 0L
        val longestFrames = sources.maxOf { it.totalFramesAtBusRate }
        return longestFrames * 1000L / PlaybackClock.OUTPUT_SAMPLE_RATE
    }

    private fun durationAtBpm(originalBpm: Int, currentBpm: Int): Long {
        if (baseDurationMs <= 0L) return 0L
        return (baseDurationMs.toDouble() * originalBpm.coerceAtLeast(1) / currentBpm.coerceAtLeast(1))
            .roundToLong()
            .coerceAtLeast(0L)
    }
}

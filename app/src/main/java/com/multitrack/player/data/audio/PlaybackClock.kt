package com.multitrack.player.data.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Logical master clock. It does not produce audio itself; instead it is the
 * *only* source of truth for "how many milliseconds of mix have been emitted
 * since the start of the session".
 *
 * The audio thread (Mixer) updates [positionFrames] atomically every time it
 * produces a chunk. The UI subscribes to [state].
 *
 * Why a class and not just a MutableStateFlow<PlaybackState> directly? Because
 * the audio thread must update position with **lock-free**, allocation-free
 * primitives. We expose AtomicLong for the hot path and convert to a Flow
 * snapshot at a low cadence for the UI.
 */
class PlaybackClock {

    /** Position in *output frames* (i.e. at OUTPUT_SAMPLE_RATE). */
    private val positionFrames = AtomicLong(0L)

    private val _state = MutableStateFlow(ClockState())
    val state: StateFlow<ClockState> = _state.asStateFlow()

    @Volatile private var running: Boolean = false
    @Volatile private var outputSampleRate: Int = OUTPUT_SAMPLE_RATE

    fun configure(outputSampleRate: Int) {
        this.outputSampleRate = outputSampleRate
    }

    fun start() {
        running = true
        publish()
    }

    fun stop() {
        running = false
        publish()
    }

    fun reset() {
        positionFrames.set(0L)
        publish()
    }

    /**
     * Called by the audio thread after each write to AudioTrack.
     * All inputs are in OUTPUT frames and channels.
     */
    fun onFramesProduced(frames: Int) {
        positionFrames.addAndGet(frames.toLong())
        // We only publish to StateFlow occasionally to avoid GC pressure.
        // The caller (Mixer) calls [publish] every ~50ms.
    }

    fun publish() {
        val frames = positionFrames.get()
        val ms = if (outputSampleRate > 0) frames * 1000L / outputSampleRate else 0L
        _state.value = ClockState(
            positionFrames = frames,
            positionMs = ms,
            running = running
        )
    }

    fun positionMsBlocking(): Long {
        val frames = positionFrames.get()
        return if (outputSampleRate > 0) frames * 1000L / outputSampleRate else 0L
    }

    data class ClockState(
        val positionFrames: Long = 0L,
        val positionMs: Long = 0L,
        val running: Boolean = false
    )

    companion object {
        /** Standardised bus sample rate. All sources are resampled to this. */
        const val OUTPUT_SAMPLE_RATE: Int = 44_100

        /** Standardised bus channel count. Mono sources are duplicated. */
        const val OUTPUT_CHANNELS: Int = 2
    }
}

package com.multitrack.player.domain.repository

import com.multitrack.player.domain.model.PlaybackSession
import com.multitrack.player.domain.model.PlaybackState
import com.multitrack.player.domain.model.MetronomeSubdivision
import kotlinx.coroutines.flow.StateFlow

/**
 * Pure abstraction over the audio engine. The ViewModel talks to this only;
 * it has zero knowledge of AudioTrack, MediaCodec, ExoPlayer, etc.
 *
 * Threading contract: every method may be called from the main thread; the
 * implementation is responsible for moving work to its own threads.
 */
interface PlaybackEngine {

    val state: StateFlow<PlaybackState>

    /** Loads all tracks of [session] and pre-decodes them into PCM. */
    suspend fun prepare(session: PlaybackSession): Result<Unit>

    /** Starts (or resumes) playback from the current position. */
    fun play()

    /** Pauses playback, keeping position. */
    fun pause()

    /** Stops playback and resets position to 0. */
    fun stop()

    /** Resets position to 0 without changing the playing/paused flag. */
    fun seekToStart()

    /** Enables/disables looping the whole mix. */
    fun setLoop(enabled: Boolean)

    /** Configures the app-level count-in used when playback starts from zero. */
    fun setCountInSettings(enabled: Boolean, beats: Int)

    fun setMetronomeVolume(volume: Float)

    fun setMetronomeSubdivision(subdivision: MetronomeSubdivision)

    /** Quantizes loop boundaries to four 4/4 bars. */
    fun setLoopQuantized(enabled: Boolean)

    /** Mutes or unmutes a single source without affecting the other tracks. */
    fun setTrackMuted(trackId: String, muted: Boolean)

    /** Enables or disables the metronome click on the left output channel. */
    fun setMetronomeEnabled(enabled: Boolean)

    /** Sets one source's linear gain in the range 0..1. */
    fun setTrackVolume(trackId: String, volume: Float)

    /** Adds/removes one track from the solo set. */
    fun setTrackSolo(trackId: String, solo: Boolean)

    /** Changes the playback speed (BPM). Clamped to engine limits. */
    fun setBpm(bpm: Int)

    /** Updates the source music tempo used as the playback-speed reference. */
    fun setOriginalBpm(bpm: Int)

    /** Releases every native resource. Idempotent. */
    fun release()
}

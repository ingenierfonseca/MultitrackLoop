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
        _state, clock.state
    ) { st, clk ->
        st.copy(positionMs = clk.positionMs)
    }.stateIn(scope, SharingStarted.Eagerly, _state.value)

    private var preparedTracks: List<AudioTrack> = emptyList()

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
                mixer.configure(sources, session.initialBpm)
                mixer.setLoop(session.isLoopEnabled)
                mixer.setMetronomeEnabled(session.isMetronomeEnabled)
                session.tracks.forEachIndexed { index, track ->
                    if (track.id in session.mutedTrackIds) mixer.setGain(index, 0f)
                }
                preparedTracks = session.tracks
                resume = false
                _state.value = _state.value.copy(
                    tracks = session.tracks,
                    currentBpm = session.initialBpm,
                    durationMs = durationMs,
                    positionMs = 0L,
                    isPlaying = false,
                    isPaused = false,
                    isLoopEnabled = session.isLoopEnabled,
                    isMetronomeEnabled = session.isMetronomeEnabled,
                    isLoading = false,
                    mutedTrackIds = session.mutedTrackIds.intersect(session.tracks.map { it.id }.toSet()),
                    error = null
                )
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
        val trackIndex = preparedTracks.indexOfFirst { it.id == trackId }
        if (trackIndex < 0) return
        mixer.setGain(trackIndex, if (muted) 0f else 1f)
        _state.update { state ->
            state.copy(
                mutedTrackIds = if (muted) {
                    state.mutedTrackIds + trackId
                } else {
                    state.mutedTrackIds - trackId
                }
            )
        }
    }

    override fun setMetronomeEnabled(enabled: Boolean) {
        mixer.setMetronomeEnabled(enabled)
        _state.update { it.copy(isMetronomeEnabled = enabled) }
    }

    override fun setBpm(bpm: Int) {
        val clamped = bpm.coerceIn(PlaybackState.MIN_BPM, PlaybackState.MAX_BPM)
        mixer.setBpm(clamped)
        _state.value = _state.value.copy(currentBpm = clamped)
    }

    override fun release() {
        mixer.release()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private fun onEngineError(error: AudioError) {
        _state.value = _state.value.copy(isPlaying = false, isPaused = false, error = error)
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
}

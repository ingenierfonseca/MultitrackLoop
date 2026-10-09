package com.multitrack.player.domain.usecase

import com.multitrack.player.domain.model.PlaybackSession
import com.multitrack.player.domain.model.PlaybackState

/**
 * Builds a PlaybackSession from raw inputs after validating business rules.
 * The ViewModel calls this on the "Continuar" tap.
 */
class CreatePlaybackSessionUseCase {
    operator fun invoke(
        tracks: List<com.multitrack.player.domain.model.AudioTrack>,
        bpm: Int
    ): Result<PlaybackSession> {
        if (tracks.isEmpty()) {
            return Result.failure(IllegalArgumentException("No tracks selected"))
        }
        if (bpm !in PlaybackState.MIN_BPM..PlaybackState.MAX_BPM) {
            return Result.failure(IllegalArgumentException("Invalid BPM"))
        }
        return Result.success(PlaybackSession(tracks = tracks, initialBpm = bpm))
    }
}

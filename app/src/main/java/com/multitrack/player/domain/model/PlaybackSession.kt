package com.multitrack.player.domain.model

/**
 * A logical playback project. It is intentionally lightweight so it can be
 * serialized into a navigation argument without leaking framework types.
 */
data class PlaybackSession(
    val tracks: List<AudioTrack>,
    val initialBpm: Int,
    val isLoopEnabled: Boolean = true,
    val mutedTrackIds: Set<String> = emptySet()
)

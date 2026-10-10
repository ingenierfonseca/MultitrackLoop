package com.multitrack.player.domain.model

/**
 * A logical playback project. It is intentionally lightweight so it can be
 * serialized into a navigation argument without leaking framework types.
 */
data class PlaybackSession(
    val tracks: List<AudioTrack> = emptyList(),
    val initialBpm: Int,
    val originalBpm: Int = initialBpm,
    val isLoopEnabled: Boolean = true,
    val isLoopQuantized: Boolean = false,
    val isMetronomeEnabled: Boolean = false,
    val mutedTrackIds: Set<String> = emptySet(),
    val soloTrackIds: Set<String> = emptySet(),
    val trackVolumes: Map<String, Float> = emptyMap()
)

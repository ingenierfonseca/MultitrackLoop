package com.multitrack.player.domain.model

data class PlaybackProject(
    val name: String,
    val tracks: List<AudioTrack>,
    val bpm: Int,
    val isLoopEnabled: Boolean,
    val mutedTrackIds: Set<String> = emptySet()
)

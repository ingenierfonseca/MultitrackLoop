package com.multitrack.player.domain.model

data class PlaybackProject(
    val name: String,
    val tracks: List<AudioTrack>,
    val bpm: Int,
    val isLoopEnabled: Boolean,
    val isMetronomeEnabled: Boolean = false,
    val mutedTrackIds: Set<String> = emptySet()
)

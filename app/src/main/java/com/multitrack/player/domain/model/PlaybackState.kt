package com.multitrack.player.domain.model

/**
 * Single source of truth for the playback UI. Mirrored by a StateFlow in the
 * presentation layer.
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isLoopEnabled: Boolean = true,
    val isMetronomeEnabled: Boolean = false,
    val currentBpm: Int = 120,
    val originalBpm: Int = currentBpm,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isLoading: Boolean = false,
    val tracks: List<AudioTrack> = emptyList(),
    val mutedTrackIds: Set<String> = emptySet(),
    val soloTrackIds: Set<String> = emptySet(),
    val trackVolumes: Map<String, Float> = emptyMap(),
    val trackMeterLevels: Map<String, TrackMeterLevel> = emptyMap(),
    val error: AudioError? = null
) {
    companion object {
        const val MIN_BPM: Int = 40
        const val MAX_BPM: Int = 300
    }
}

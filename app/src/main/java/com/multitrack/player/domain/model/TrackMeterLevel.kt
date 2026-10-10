package com.multitrack.player.domain.model

data class TrackMeterLevel(
    val rms: Float = 0f,
    val peak: Float = 0f
) {
    companion object {
        val Silent = TrackMeterLevel()
    }
}

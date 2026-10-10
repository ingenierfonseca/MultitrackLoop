package com.multitrack.player.domain.model

enum class MetronomeSubdivision {
    EIGHTH_NOTE,
    SIXTEENTH_NOTE
}

data class AppSettings(
    val countInEnabled: Boolean = false,
    val countInBeats: Int = 4,
    val metronomeVolume: Float = 0.45f,
    val metronomeSubdivision: MetronomeSubdivision = MetronomeSubdivision.EIGHTH_NOTE
)

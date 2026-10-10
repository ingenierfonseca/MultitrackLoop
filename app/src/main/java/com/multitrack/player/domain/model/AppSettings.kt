package com.multitrack.player.domain.model

enum class MetronomeSubdivision(val pulsesPerBeat: Int) {
    QUARTER_NOTE(1),
    EIGHTH_NOTE(2)
}

data class AppSettings(
    val countInEnabled: Boolean = false,
    val countInBeats: Int = 4,
    val metronomeVolume: Float = 0.45f,
    val metronomeSubdivision: MetronomeSubdivision = MetronomeSubdivision.QUARTER_NOTE
)

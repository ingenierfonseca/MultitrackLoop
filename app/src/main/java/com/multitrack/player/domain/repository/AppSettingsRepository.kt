package com.multitrack.player.domain.repository

import com.multitrack.player.domain.model.AppSettings
import com.multitrack.player.domain.model.MetronomeSubdivision
import kotlinx.coroutines.flow.Flow

interface AppSettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setCountInEnabled(enabled: Boolean)
    suspend fun setCountInBeats(beats: Int)
    suspend fun setMetronomeVolume(volume: Float)
    suspend fun setMetronomeSubdivision(subdivision: MetronomeSubdivision)
}

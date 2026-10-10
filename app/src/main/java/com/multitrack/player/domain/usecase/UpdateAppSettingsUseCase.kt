package com.multitrack.player.domain.usecase

import com.multitrack.player.domain.model.AppSettings
import com.multitrack.player.domain.model.MetronomeSubdivision
import com.multitrack.player.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow

class UpdateAppSettingsUseCase(
    private val repository: AppSettingsRepository
) {
    val settings: Flow<AppSettings> = repository.settings

    suspend fun setCountInEnabled(enabled: Boolean) = repository.setCountInEnabled(enabled)

    suspend fun setCountInBeats(beats: Int) = repository.setCountInBeats(beats.coerceIn(1, 4))

    suspend fun setMetronomeVolume(volume: Float) =
        repository.setMetronomeVolume(volume.coerceIn(0f, 1f))

    suspend fun setMetronomeSubdivision(subdivision: MetronomeSubdivision) =
        repository.setMetronomeSubdivision(subdivision)
}

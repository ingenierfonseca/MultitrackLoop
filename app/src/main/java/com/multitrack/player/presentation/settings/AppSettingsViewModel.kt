package com.multitrack.player.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.multitrack.player.domain.model.AppSettings
import com.multitrack.player.domain.model.MetronomeSubdivision
import com.multitrack.player.domain.usecase.UpdateAppSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppSettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoaded: Boolean = false
)

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val updateSettings: UpdateAppSettingsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppSettingsUiState())
    val uiState: StateFlow<AppSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            updateSettings.settings.collect { settings ->
                _uiState.value = AppSettingsUiState(settings = settings, isLoaded = true)
            }
        }
    }

    fun setCountInEnabled(enabled: Boolean) {
        updateState { it.copy(countInEnabled = enabled) }
        viewModelScope.launch { updateSettings.setCountInEnabled(enabled) }
    }

    fun setCountInBeats(beats: Int) {
        val safeBeats = beats.coerceIn(1, 4)
        updateState { it.copy(countInBeats = safeBeats) }
        viewModelScope.launch { updateSettings.setCountInBeats(safeBeats) }
    }

    fun setMetronomeVolume(volume: Float) {
        val safeVolume = volume.coerceIn(0f, 1f)
        updateState { it.copy(metronomeVolume = safeVolume) }
        viewModelScope.launch { updateSettings.setMetronomeVolume(safeVolume) }
    }

    fun setMetronomeSubdivision(subdivision: MetronomeSubdivision) {
        updateState { it.copy(metronomeSubdivision = subdivision) }
        viewModelScope.launch { updateSettings.setMetronomeSubdivision(subdivision) }
    }

    private fun updateState(transform: (AppSettings) -> AppSettings) {
        _uiState.update { current -> current.copy(settings = transform(current.settings)) }
    }
}

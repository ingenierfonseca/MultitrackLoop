package com.multitrack.player.presentation.screens.trackselection

import com.multitrack.player.domain.model.AudioError
import com.multitrack.player.domain.model.AudioTrack

/**
 * Discriminated UI state for the track selection screen.
 * The sealed hierarchy ensures the UI exhaustively handles every situation.
 */
sealed interface TrackSelectionUiState {
    data object Idle : TrackSelectionUiState

    data class Editing(
        val tracks: List<AudioTrack> = emptyList(),
        val bpmInput: String = "120",
        val bpmError: String? = null,
        val tracksError: String? = null,
        val resolving: Boolean = false,
        val transientError: AudioError? = null
    ) : TrackSelectionUiState {
        val isContinueEnabled: Boolean
            get() = tracks.isNotEmpty() && bpmError == null
    }

    data class Validated(
        val tracks: List<AudioTrack>,
        val bpm: Int
    ) : TrackSelectionUiState
}

package com.multitrack.player.presentation.screens.trackselection

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.multitrack.player.domain.model.PlaybackState
import com.multitrack.player.domain.usecase.CreatePlaybackSessionUseCase
import com.multitrack.player.domain.usecase.ResolvePickedTrackUseCase
import com.multitrack.player.domain.usecase.ValidateBpmUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackSelectionViewModel @Inject constructor(
    private val resolvePickedTrack: ResolvePickedTrackUseCase,
    private val createSession: CreatePlaybackSessionUseCase,
    private val validateBpm: ValidateBpmUseCase,
    private val contentResolver: ContentResolver
) : ViewModel() {

    private val _state = MutableStateFlow<TrackSelectionUiState>(
        TrackSelectionUiState.Editing()
    )
    val state: StateFlow<TrackSelectionUiState> = _state.asStateFlow()

    private val _events = Channel<NavigationEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Called when the system returns the URIs from ACTION_OPEN_DOCUMENT. */
    fun onUrisPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { (it as? TrackSelectionUiState.Editing)?.copy(resolving = true) ?: it }
            val resolved = uris.mapNotNull { uri ->
                val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "audio"
                val mime = contentResolver.getType(uri)
                val result = resolvePickedTrack(uri.toString(), name, mime)
                result.getOrNull()
            }
            _state.update { current ->
                if (current is TrackSelectionUiState.Editing) {
                    val merged = current.tracks + resolved
                    current.copy(tracks = merged, resolving = false)
                } else current
            }
        }
    }

    fun onBpmChanged(text: String) {
        _state.update { current ->
            if (current is TrackSelectionUiState.Editing) {
                val parsed = text.toIntOrNull()
                val err = when {
                    text.isBlank() -> null // soft error until user tries to continue
                    parsed == null -> "invalid"
                    !validateBpm(parsed) ->
                        if (parsed < PlaybackState.MIN_BPM) "min" else "max"
                    else -> null
                }
                current.copy(bpmInput = text, bpmError = err)
            } else current
        }
    }

    fun onRemoveTrack(id: String) {
        _state.update { current ->
            if (current is TrackSelectionUiState.Editing) {
                current.copy(tracks = current.tracks.filterNot { it.id == id })
            } else current
        }
    }

    fun onContinue() {
        val current = _state.value as? TrackSelectionUiState.Editing ?: return
        val parsed = current.bpmInput.toIntOrNull()
        if (current.tracks.isEmpty()) {
            _state.update { current.copy(tracksError = "empty") }
            return
        }
        if (!validateBpm(parsed)) {
            _state.update { current.copy(bpmError = "invalid") }
            return
        }
        val bpm = parsed!!
        createSession(current.tracks, bpm)
            .onSuccess { session ->
                _state.update { TrackSelectionUiState.Validated(session.tracks, bpm) }
                viewModelScope.launch { _events.send(NavigationEvent.ToPlayback(session)) }
            }
            .onFailure {
                _state.update { current.copy(bpmError = "invalid") }
            }
    }

    fun bpmErrorMessage(): String? {
        val s = _state.value as? TrackSelectionUiState.Editing ?: return null
        return when (s.bpmError) {
            "min" -> "min"
            "max" -> "max"
            "invalid" -> "invalid"
            else -> null
        }
    }

    /**
     * SAF URIs expose the user-visible filename through [OpenableColumns.DISPLAY_NAME].
     * Falls back to the URI's last path segment (often unreadable on content:// URIs)
     * and finally to a generic label.
     */
    private fun queryDisplayName(uri: Uri): String? {
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
    }

    sealed interface NavigationEvent {
        data class ToPlayback(val session: com.multitrack.player.domain.model.PlaybackSession) : NavigationEvent
    }
}

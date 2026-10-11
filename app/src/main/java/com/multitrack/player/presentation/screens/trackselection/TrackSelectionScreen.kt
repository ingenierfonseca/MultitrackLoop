package com.multitrack.player.presentation.screens.trackselection

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.multitrack.player.presentation.components.ProjectTempoDialog


@Composable
fun TrackSelectionScreen(
    onNavigateToPlayback: (com.multitrack.player.domain.model.PlaybackSession) -> Unit,
    viewModel: TrackSelectionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TrackSelectionViewModel.NavigationEvent.ToPlayback ->
                    onNavigateToPlayback(event.session)
            }
        }
    }

    Scaffold { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            val editing = state as? TrackSelectionUiState.Editing
            ProjectTempoDialog(
                bpmInput = editing?.bpmInput ?: "120",
                error = editing?.bpmError,
                onDismiss = {},
                onContinue = viewModel::onContinue,
                onBpmChange = viewModel::onBpmChanged
            )
        }
    }
}

@Preview
@Composable
fun TrackSelectionScreenPreview() {
    Scaffold { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            ProjectTempoDialog(
                bpmInput = "120",
                error = "2",
                onDismiss = {},
                onContinue = { },
                onBpmChange = {}
            )
        }
    }
}

package com.multitrack.player.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.multitrack.player.R
import com.multitrack.player.domain.model.PlaybackState

@Composable
fun ProjectTempoDialog(
    bpmInput: String,
    error: String?,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    onBpmChange: (String) -> Unit,
    titleRes: Int = R.string.track_selection_title,
    continueEnabled: Boolean = true
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .background(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = stringResource(titleRes),
                fontSize = 25.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.SemiBold
            )

            TempoCard(
                bpmInput = bpmInput,
                error = bpmErrorString(error),
                onBpmChange = onBpmChange
            )

            Button(
                onClick = onContinue,
                enabled = continueEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { testTag = "continue-button" },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    stringResource(R.string.continue_action),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun TempoCard(
    bpmInput: String,
    error: String?,
    onBpmChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.tempo_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = bpmInput,
                onValueChange = onBpmChange,
                label = { Text(stringResource(R.string.bpm_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = error != null,
                supportingText = {
                    if (error != null) Text(error)
                    else Text(
                        stringResource(
                            R.string.bpm_range,
                            PlaybackState.MIN_BPM,
                            PlaybackState.MAX_BPM
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { testTag = "bpm-input" }
            )
        }
    }
}

@Composable
private fun bpmErrorString(code: String?): String? = when (code) {
    "min" -> stringResource(R.string.error_min_bpm, PlaybackState.MIN_BPM)
    "max" -> stringResource(R.string.error_max_bpm, PlaybackState.MAX_BPM)
    "invalid" -> stringResource(R.string.error_invalid_bpm)
    else -> null
}

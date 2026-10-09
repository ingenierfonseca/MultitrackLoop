package com.multitrack.player.domain.usecase

import com.multitrack.player.domain.model.AudioFormat
import com.multitrack.player.domain.model.AudioTrack
import com.multitrack.player.domain.repository.AudioTrackRepository
import java.util.UUID

/**
 * Validates a file picked by the user (mp3/wav) and resolves its duration
 * by deferring to the data layer.
 */
class ResolvePickedTrackUseCase(
    private val repository: AudioTrackRepository
) {
    suspend operator fun invoke(
        uri: String,
        displayName: String,
        mime: String?
    ): Result<AudioTrack> {
        val format = detectFormat(displayName, mime)
            ?: return Result.failure(IllegalArgumentException("Formato no soportado"))
        return repository.resolveTrack(uri, displayName, mime)
            .map { it.copy(format = format) }
    }

    private fun detectFormat(name: String, mime: String?): AudioFormat? {
        val lower = name.lowercase()
        val m = mime?.lowercase()
        return when {
            lower.endsWith(".wav") || m == "audio/wav" || m == "audio/x-wav" || m == "audio/wave" -> AudioFormat.WAV
            lower.endsWith(".mp3") || m == "audio/mpeg" || m == "audio/mp3" -> AudioFormat.MP3
            else -> null
        }
    }
}

package com.multitrack.player.data.datasource

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.multitrack.player.domain.model.AudioError
import com.multitrack.player.domain.model.AudioFormat
import com.multitrack.player.domain.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Thin wrapper around ContentResolver for SAF / media-store URIs.
 *
 * It does NOT copy the file; it only reads metadata. The actual PCM read
 * happens later, lazily, inside the playback engine via openInputStream.
 *
 * Format is set to WAV as a placeholder; [ResolvePickedTrackUseCase] overrides
 * it with the real one detected from the file extension or MIME type.
 */
class ContentResolverDataSource(
    private val contentResolver: ContentResolver
) {

    suspend fun queryMetadata(uri: Uri): Result<AudioTrack> = withContext(Dispatchers.IO) {
        runCatching {
            val name = queryDisplayName(uri) ?: "unknown"
            AudioTrack(
                id = UUID.randomUUID().toString(),
                name = name,
                uri = uri.toString(),
                durationMs = null,
                format = AudioFormat.WAV
            )
        }.recoverCatching {
            throw AudioError.FileNotFound
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
    }

    fun openInputStream(uri: Uri): java.io.InputStream? = contentResolver.openInputStream(uri)
}

package com.multitrack.player.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.multitrack.player.data.serialization.PlaybackProjectJsonCodec
import com.multitrack.player.domain.model.PlaybackProject
import com.multitrack.player.domain.repository.PlaybackProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlaybackProjectRepositoryImpl(
    private val contentResolver: ContentResolver
) : PlaybackProjectRepository {

    override suspend fun save(project: PlaybackProject, destinationUri: String) =
        withContext(Dispatchers.IO) {
            val output = contentResolver.openOutputStream(Uri.parse(destinationUri), "wt")
                ?: error("Could not open the project file for writing")
            output.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(PlaybackProjectJsonCodec.encode(project))
            }
        }

    override suspend fun load(sourceUri: String): PlaybackProject = withContext(Dispatchers.IO) {
        val uri = Uri.parse(sourceUri)
        val fileName = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameColumn >= 0) cursor.getString(nameColumn) else null
            } else null
        }
        require(fileName?.endsWith(".mtl", ignoreCase = true) == true) {
            "Select a .mtl project file"
        }

        val input = contentResolver.openInputStream(uri) ?: error("Could not open the project file")
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            PlaybackProjectJsonCodec.decode(reader.readText())
        }
    }
}

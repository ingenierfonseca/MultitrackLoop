package com.multitrack.player.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.multitrack.player.data.serialization.PlaybackProjectJsonCodec
import com.multitrack.player.domain.model.PlaybackProjectError
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
        val input = contentResolver.openInputStream(uri) ?: error("Could not open the project file")
        val content = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        try {
            PlaybackProjectJsonCodec.decode(content)
        } catch (error: kotlinx.serialization.SerializationException) {
            throw PlaybackProjectError.InvalidFile(error)
        } catch (error: IllegalArgumentException) {
            throw PlaybackProjectError.InvalidFile(error)
        }
    }
}

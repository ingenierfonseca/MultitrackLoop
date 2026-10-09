package com.multitrack.player.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.multitrack.player.data.datasource.ContentResolverDataSource
import com.multitrack.player.domain.model.AudioTrack
import com.multitrack.player.domain.repository.AudioTrackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioTrackRepositoryImpl(
    private val dataSource: ContentResolverDataSource,
    @Suppress("unused") private val contentResolver: ContentResolver
) : AudioTrackRepository {

    override suspend fun resolveTrack(
        uri: String,
        displayName: String,
        mime: String?
    ): Result<AudioTrack> = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = Uri.parse(uri)
            // Make sure the URI is actually openable; throws otherwise.
            dataSource.openInputStream(parsed)?.use { /* no-op */ }
                ?: error("Cannot open stream for $uri")
            dataSource.queryMetadata(parsed).getOrThrow()
        }
    }
}

package com.multitrack.player.presentation.navigation

import com.multitrack.player.domain.model.AudioFormat
import com.multitrack.player.domain.model.AudioTrack
import com.multitrack.player.domain.model.PlaybackSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object NavRoutes {
    const val TRACK_SELECTION = "track_selection"
    const val PLAYBACK = "playback/{session}"
    fun playback(sessionJson: String): String = "playback/${java.net.URLEncoder.encode(sessionJson, "UTF-8")}"
}

@Serializable
data class SerializableTrack(
    val id: String,
    val name: String,
    val uri: String,
    val durationMs: Long?,
    val format: String
)

@Serializable
data class SerializableSession(
    val tracks: List<SerializableTrack>,
    val bpm: Int,
    val isLoopEnabled: Boolean = true,
    val mutedTrackIds: List<String> = emptyList()
)

fun PlaybackSession.toSerializable(): SerializableSession =
    SerializableSession(
        tracks = tracks.map {
            SerializableTrack(it.id, it.name, it.uri, it.durationMs, it.format.name)
        },
        bpm = initialBpm,
        isLoopEnabled = isLoopEnabled,
        mutedTrackIds = mutedTrackIds.toList()
    )

fun SerializableSession.toDomain(): PlaybackSession =
    PlaybackSession(
        tracks = tracks.map {
            AudioTrack(
                id = it.id,
                name = it.name,
                uri = it.uri,
                durationMs = it.durationMs,
                format = AudioFormat.valueOf(it.format)
            )
        },
        initialBpm = bpm,
        isLoopEnabled = isLoopEnabled,
        mutedTrackIds = mutedTrackIds.toSet()
    )

object SessionCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(session: PlaybackSession): String =
        java.net.URLEncoder.encode(json.encodeToString(session.toSerializable()), "UTF-8")
    fun decode(serialized: String): PlaybackSession =
        json.decodeFromString<SerializableSession>(java.net.URLDecoder.decode(serialized, "UTF-8")).toDomain()
}

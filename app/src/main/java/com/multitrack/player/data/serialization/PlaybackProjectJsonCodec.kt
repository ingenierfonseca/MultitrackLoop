package com.multitrack.player.data.serialization

import com.multitrack.player.domain.model.AudioFormat
import com.multitrack.player.domain.model.AudioTrack
import com.multitrack.player.domain.model.PlaybackProject
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class ProjectDocument(
    val version: Int = 1,
    val name: String,
    val bpm: Int,
    val isLoopEnabled: Boolean,
    val mutedTrackIds: List<String> = emptyList(),
    val tracks: List<ProjectTrack>
)

@Serializable
private data class ProjectTrack(
    val id: String,
    val name: String,
    val uri: String,
    val durationMs: Long?,
    val format: String
)

object PlaybackProjectJsonCodec {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(project: PlaybackProject): String = json.encodeToString(
        ProjectDocument(
            name = project.name,
            bpm = project.bpm,
            isLoopEnabled = project.isLoopEnabled,
            mutedTrackIds = project.mutedTrackIds.toList(),
            tracks = project.tracks.map { track ->
                ProjectTrack(
                    id = track.id,
                    name = track.name,
                    uri = track.uri,
                    durationMs = track.durationMs,
                    format = track.format.name
                )
            }
        )
    )

    fun decode(content: String): PlaybackProject {
        val document = json.decodeFromString<ProjectDocument>(content)
        require(document.version == 1) { "Unsupported project version" }
        require(document.tracks.isNotEmpty()) { "Project has no tracks" }

        return PlaybackProject(
            name = document.name,
            bpm = document.bpm,
            isLoopEnabled = document.isLoopEnabled,
            mutedTrackIds = document.mutedTrackIds.toSet(),
            tracks = document.tracks.map { track ->
                AudioTrack(
                    id = track.id,
                    name = track.name,
                    uri = track.uri,
                    durationMs = track.durationMs,
                    format = AudioFormat.valueOf(track.format)
                )
            }
        )
    }
}

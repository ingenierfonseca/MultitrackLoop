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
    val originalBpm: Int = bpm,
    val isLoopEnabled: Boolean,
    val isLoopQuantized: Boolean = false,
    val isMetronomeEnabled: Boolean = false,
    val mutedTrackIds: List<String> = emptyList(),
    val soloTrackIds: List<String> = emptyList(),
    val trackVolumes: Map<String, Float> = emptyMap(),
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
            originalBpm = project.originalBpm,
            isLoopEnabled = project.isLoopEnabled,
            isLoopQuantized = project.isLoopQuantized,
            isMetronomeEnabled = project.isMetronomeEnabled,
            mutedTrackIds = project.mutedTrackIds.toList(),
            soloTrackIds = project.soloTrackIds.toList(),
            trackVolumes = project.trackVolumes,
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
            originalBpm = document.originalBpm,
            isLoopEnabled = document.isLoopEnabled,
            isLoopQuantized = document.isLoopQuantized,
            isMetronomeEnabled = document.isMetronomeEnabled,
            mutedTrackIds = document.mutedTrackIds.toSet(),
            soloTrackIds = document.soloTrackIds.toSet(),
            trackVolumes = document.trackVolumes,
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

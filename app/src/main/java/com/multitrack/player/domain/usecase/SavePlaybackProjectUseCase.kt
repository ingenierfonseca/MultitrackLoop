package com.multitrack.player.domain.usecase

import com.multitrack.player.domain.model.PlaybackProject
import com.multitrack.player.domain.model.PlaybackState
import com.multitrack.player.domain.repository.PlaybackProjectRepository

class SavePlaybackProjectUseCase(
    private val repository: PlaybackProjectRepository
) {
    suspend operator fun invoke(project: PlaybackProject, destinationUri: String) {
        require(project.name.isNotBlank()) { "Project name is required" }
        require(project.tracks.isNotEmpty()) { "Project has no tracks" }
        require(project.bpm in PlaybackState.MIN_BPM..PlaybackState.MAX_BPM) {
            "Project BPM is outside the supported range"
        }
        require(destinationUri.isNotBlank()) { "Project destination is required" }
        repository.save(project, destinationUri)
    }
}

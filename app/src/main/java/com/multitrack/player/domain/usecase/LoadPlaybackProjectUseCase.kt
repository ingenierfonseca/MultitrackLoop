package com.multitrack.player.domain.usecase

import com.multitrack.player.domain.model.PlaybackProject
import com.multitrack.player.domain.model.PlaybackState
import com.multitrack.player.domain.repository.PlaybackProjectRepository

class LoadPlaybackProjectUseCase(
    private val repository: PlaybackProjectRepository
) {
    suspend operator fun invoke(sourceUri: String): PlaybackProject {
        val project = repository.load(sourceUri)
        require(project.tracks.isNotEmpty()) { "Project has no tracks" }
        require(project.bpm in PlaybackState.MIN_BPM..PlaybackState.MAX_BPM) {
            "Project BPM is outside the supported range"
        }
        require(project.originalBpm in PlaybackState.MIN_BPM..PlaybackState.MAX_BPM) {
            "Project original BPM is outside the supported range"
        }
        return project
    }
}

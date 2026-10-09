package com.multitrack.player.domain.repository

import com.multitrack.player.domain.model.PlaybackProject

interface PlaybackProjectRepository {
    suspend fun save(project: PlaybackProject, destinationUri: String)
    suspend fun load(sourceUri: String): PlaybackProject
}

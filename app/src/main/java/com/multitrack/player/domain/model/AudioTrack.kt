package com.multitrack.player.domain.model

/**
 * Pure domain model. UI passes identifiers only between screens; the actual
 * playback engine resolves the URI when the session is built.
 */
data class AudioTrack(
    val id: String,
    val name: String,
    val uri: String,
    val durationMs: Long?,
    val format: AudioFormat
)

enum class AudioFormat { MP3, WAV }

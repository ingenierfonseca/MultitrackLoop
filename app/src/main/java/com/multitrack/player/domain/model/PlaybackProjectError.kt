package com.multitrack.player.domain.model

sealed class PlaybackProjectError(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class InvalidFile(cause: Throwable) :
        PlaybackProjectError("The selected file is not a valid .mtl project", cause)
}

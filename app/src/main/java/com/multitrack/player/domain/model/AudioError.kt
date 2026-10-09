package com.multitrack.player.domain.model

/**
 * Domain-level error model. The data layer maps framework exceptions to one
 * of these. The UI translates to user-facing messages.
 *
 * Extends [Exception] so instances are throwable and can flow through
 * coroutines / try-catch blocks without wrapping.
 */
sealed class AudioError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    data object UnsupportedFormat : AudioError()
    data object FileNotFound : AudioError()
    data object PermissionDenied : AudioError()
    data class InvalidAudio(val causeMessage: String?) : AudioError(causeMessage)
    data class DecoderError(val causeMessage: String?) : AudioError(causeMessage)
    data class PlaybackError(val causeMessage: String?) : AudioError(causeMessage)
    data class Unknown(val causeMessage: String?) : AudioError(causeMessage)
}

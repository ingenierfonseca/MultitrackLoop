package com.multitrack.player.domain.usecase

import com.multitrack.player.domain.model.PlaybackState

/**
 * Pure validation helper. Returns true when the integer is within the
 * allowed BPM range. Kept as a use case so it can be tested in isolation
 * and reused by any caller that needs BPM validation.
 */
class ValidateBpmUseCase {
    operator fun invoke(bpm: Int?): Boolean {
        if (bpm == null) return false
        return bpm in PlaybackState.MIN_BPM..PlaybackState.MAX_BPM
    }
}

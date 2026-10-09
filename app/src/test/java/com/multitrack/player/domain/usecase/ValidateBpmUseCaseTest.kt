package com.multitrack.player.domain.usecase

import com.multitrack.player.domain.model.PlaybackState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateBpmUseCaseTest {

    private val useCase = ValidateBpmUseCase()

    @Test
    fun `null bpm is invalid`() {
        assertFalse(useCase(null))
    }

    @Test
    fun `bpm below minimum is invalid`() {
        assertFalse(useCase(PlaybackState.MIN_BPM - 1))
        assertFalse(useCase(0))
        assertFalse(useCase(-10))
    }

    @Test
    fun `bpm above maximum is invalid`() {
        assertFalse(useCase(PlaybackState.MAX_BPM + 1))
        assertFalse(useCase(1000))
    }

    @Test
    fun `bpm within range is valid`() {
        assertTrue(useCase(PlaybackState.MIN_BPM))
        assertTrue(useCase(120))
        assertTrue(useCase(PlaybackState.MAX_BPM))
    }
}

package com.multitrack.player.domain.usecase

import com.multitrack.player.domain.model.AudioFormat
import com.multitrack.player.domain.model.AudioTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatePlaybackSessionUseCaseTest {

    private val useCase = CreatePlaybackSessionUseCase()

    private fun track(id: String) = AudioTrack(
        id = id,
        name = "$id.wav",
        uri = "content://$id",
        durationMs = 1000L,
        format = AudioFormat.WAV
    )

    @Test
    fun `empty tracks returns failure`() {
        val r = useCase(emptyList(), 120)
        assertTrue(r.isFailure)
    }

    @Test
    fun `bpm below range returns failure`() {
        val r = useCase(listOf(track("1")), 10)
        assertTrue(r.isFailure)
    }

    @Test
    fun `bpm above range returns failure`() {
        val r = useCase(listOf(track("1")), 999)
        assertTrue(r.isFailure)
    }

    @Test
    fun `valid input returns session with same bpm and tracks`() {
        val r = useCase(listOf(track("1"), track("2")), 140)
        assertTrue(r.isSuccess)
        val s = r.getOrThrow()
        assertEquals(140, s.initialBpm)
        assertEquals(2, s.tracks.size)
    }
}

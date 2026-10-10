package com.multitrack.player.domain.usecase

import com.multitrack.player.domain.model.PlaybackState
import com.multitrack.player.domain.model.MetronomeSubdivision
import com.multitrack.player.domain.repository.PlaybackEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackControlUseCaseTest {

    private val engine: PlaybackEngine = mockk(relaxed = true)
    private val state = MutableStateFlow(PlaybackState(currentBpm = 120))
    private val useCase = PlaybackControlUseCase(engine)

    init {
        every { engine.state } returns state
    }

    @Test
    fun `increaseBpm increments and notifies engine`() {
        val newBpm = useCase.increaseBpm(120)
        assertEquals(121, newBpm)
        verify { engine.setBpm(121) }
    }

    @Test
    fun `increaseBpm clamps to MAX_BPM`() {
        val newBpm = useCase.increaseBpm(PlaybackState.MAX_BPM)
        assertEquals(PlaybackState.MAX_BPM, newBpm)
        verify { engine.setBpm(PlaybackState.MAX_BPM) }
    }

    @Test
    fun `decreaseBpm decrements and notifies engine`() {
        val newBpm = useCase.decreaseBpm(120)
        assertEquals(119, newBpm)
        verify { engine.setBpm(119) }
    }

    @Test
    fun `decreaseBpm clamps to MIN_BPM`() {
        val newBpm = useCase.decreaseBpm(PlaybackState.MIN_BPM - 5)
        assertEquals(PlaybackState.MIN_BPM, newBpm)
        verify { engine.setBpm(PlaybackState.MIN_BPM) }
    }

    @Test
    fun `setLoop forwards to engine`() {
        useCase.setLoop(true)
        verify { engine.setLoop(true) }
    }

    @Test
    fun `loop quantization forwards to engine`() {
        useCase.setLoopQuantized(true)
        verify { engine.setLoopQuantized(true) }
    }

    @Test
    fun `track mute forwards selected id and state to engine`() {
        useCase.setTrackMuted("track-1", true)
        verify { engine.setTrackMuted("track-1", true) }
    }

    @Test
    fun `metronome toggle forwards state to engine`() {
        useCase.setMetronomeEnabled(true)
        verify { engine.setMetronomeEnabled(true) }
    }

    @Test
    fun `metronome settings forward volume and subdivision`() {
        useCase.setMetronomeVolume(0.7f)
        useCase.setMetronomeSubdivision(MetronomeSubdivision.EIGHTH_NOTE)

        verify { engine.setMetronomeVolume(0.7f) }
        verify { engine.setMetronomeSubdivision(MetronomeSubdivision.EIGHTH_NOTE) }
    }

    @Test
    fun `track volume forwards to engine`() {
        useCase.setTrackVolume("track-1", 0.6f)
        verify { engine.setTrackVolume("track-1", 0.6f) }
    }

    @Test
    fun `track solo forwards to engine`() {
        useCase.setTrackSolo("track-1", true)
        verify { engine.setTrackSolo("track-1", true) }
    }

    @Test
    fun `play pause stop forward to engine`() {
        useCase.play()
        useCase.pause()
        useCase.stop()
        useCase.seekToStart()
        verify { engine.play() }
        verify { engine.pause() }
        verify { engine.stop() }
        verify { engine.seekToStart() }
    }
}

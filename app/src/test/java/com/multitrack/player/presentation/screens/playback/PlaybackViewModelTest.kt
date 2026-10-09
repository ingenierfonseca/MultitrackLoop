package com.multitrack.player.presentation.screens.playback

import androidx.lifecycle.ViewModelStore
import com.multitrack.player.domain.model.PlaybackSession
import com.multitrack.player.domain.model.PlaybackState
import com.multitrack.player.domain.repository.PlaybackEngine
import com.multitrack.player.domain.usecase.PlaybackControlUseCase
import com.multitrack.player.domain.usecase.LoadPlaybackProjectUseCase
import com.multitrack.player.domain.usecase.ResolvePickedTrackUseCase
import com.multitrack.player.domain.usecase.SavePlaybackProjectUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackViewModelTest {

    private val engine: PlaybackEngine = mockk(relaxed = true)
    private val control: PlaybackControlUseCase = mockk(relaxed = true)
    private val resolveTrack: ResolvePickedTrackUseCase = mockk(relaxed = true)
    private val saveProject: SavePlaybackProjectUseCase = mockk(relaxed = true)
    private val loadProject: LoadPlaybackProjectUseCase = mockk(relaxed = true)
    private val state = MutableStateFlow(PlaybackState(currentBpm = 120))

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { engine.state } returns state
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `play when not playing triggers engine play`() {
        state.value = state.value.copy(isPlaying = false)
        val vm = viewModel()
        vm.onPlayPause()
        verify { control.play() }
    }

    @Test
    fun `pause when playing triggers engine pause`() {
        state.value = state.value.copy(isPlaying = true)
        val vm = viewModel()
        vm.onPlayPause()
        verify { control.pause() }
    }

    @Test
    fun `stop and restart and loop forward to use case`() {
        val vm = viewModel()
        vm.onStop()
        vm.onRestart()
        vm.onToggleLoop(false)
        verify { control.stop() }
        verify { control.seekToStart() }
        verify { control.setLoop(false) }
    }

    @Test
    fun `bpm plus and minus delegate to control with current value`() {
        state.value = state.value.copy(currentBpm = 120)
        val vm = viewModel()
        vm.onIncreaseBpm()
        verify { control.increaseBpm(120) }
        vm.onDecreaseBpm()
        verify { control.decreaseBpm(120) }
    }

    @Test
    fun `bindSession calls engine prepare`() {
        val vm = viewModel()
        vm.bindSession(PlaybackSession(emptyList(), 120))
        verify { engine.prepare(any()) }
    }

    @Test
    fun `onCleared releases engine`() {
        val vm = viewModel()
        ViewModelStore().apply {
            put("playback", vm)
            clear()
        }
        verify { engine.release() }
    }

    private fun viewModel() = PlaybackViewModel(
        engine,
        control,
        resolveTrack,
        saveProject,
        loadProject
    )
}

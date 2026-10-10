package com.multitrack.player.presentation.screens.playback

import androidx.lifecycle.ViewModelStore
import com.multitrack.player.domain.model.AppSettings
import com.multitrack.player.domain.model.MetronomeSubdivision
import com.multitrack.player.domain.model.PlaybackSession
import com.multitrack.player.domain.model.PlaybackState
import com.multitrack.player.domain.repository.PlaybackEngine
import com.multitrack.player.domain.usecase.PlaybackControlUseCase
import com.multitrack.player.domain.usecase.LoadPlaybackProjectUseCase
import com.multitrack.player.domain.usecase.ResolvePickedTrackUseCase
import com.multitrack.player.domain.usecase.SavePlaybackProjectUseCase
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `loop quantization toggle forwards to use case`() {
        viewModel().onToggleLoopQuantized(true)

        verify { control.setLoopQuantized(true) }
    }

    @Test
    fun `track mute toggles only the requested track`() {
        val track = com.multitrack.player.domain.model.AudioTrack(
            id = "track-1",
            name = "Drums.wav",
            uri = "content://track-1",
            durationMs = 1_000L,
            format = com.multitrack.player.domain.model.AudioFormat.WAV
        )
        state.value = state.value.copy(tracks = listOf(track))

        viewModel().onToggleTrackMute(track.id)

        verify { control.setTrackMuted(track.id, true) }
    }

    @Test
    fun `metronome tap toggles current engine setting`() {
        state.value = state.value.copy(isMetronomeEnabled = false)

        viewModel().onToggleMetronome()

        verify { control.setMetronomeEnabled(true) }
    }

    @Test
    fun `app metronome settings are applied to engine controls`() {
        viewModel().applyAppSettings(
            AppSettings(
                countInEnabled = true,
                countInBeats = 3,
                metronomeVolume = 0.7f,
                metronomeSubdivision = MetronomeSubdivision.EIGHTH_NOTE
            )
        )

        verify { control.setCountInSettings(true, 3) }
        verify { control.setMetronomeVolume(0.7f) }
        verify { control.setMetronomeSubdivision(MetronomeSubdivision.EIGHTH_NOTE) }
    }

    @Test
    fun `volume and solo actions target one track`() {
        val track = com.multitrack.player.domain.model.AudioTrack(
            id = "track-1",
            name = "Drums.wav",
            uri = "content://track-1",
            durationMs = 1_000L,
            format = com.multitrack.player.domain.model.AudioFormat.WAV
        )
        state.value = state.value.copy(tracks = listOf(track))

        viewModel().apply {
            onTrackVolumeChanged(track.id, 0.4f)
            onToggleTrackSolo(track.id)
        }

        verify { control.setTrackVolume(track.id, 0.4f) }
        verify { control.setTrackSolo(track.id, true) }
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
    fun `bindSession calls engine prepare`() = runTest {
        coEvery { engine.prepare(any()) } returns Result.success(Unit)
        val vm = viewModel()
        vm.bindSession(PlaybackSession(emptyList(), 120))
        coVerify { engine.prepare(any()) }
    }

    @Test
    fun `creating an empty project completes without waiting for a loading transition`() = runTest {
        coEvery { engine.prepare(any()) } coAnswers {
            val session = firstArg<PlaybackSession>()
            state.value = state.value.copy(isLoading = true)
            state.value = state.value.copy(
                isLoading = false,
                tracks = session.tracks,
                currentBpm = session.initialBpm,
                originalBpm = session.originalBpm
            )
            Result.success(Unit)
        }

        val vm = viewModel()
        vm.createNewProject(132)

        assertFalse(vm.projectUiState.value.isBusy)
        assertNull(vm.projectUiState.value.savedUri)
        coVerify {
            engine.prepare(match { session ->
                session.tracks.isEmpty() &&
                    session.initialBpm == 132 &&
                    session.originalBpm == 132
            })
        }
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

package com.multitrack.player.data.serialization

import com.multitrack.player.domain.model.AudioFormat
import com.multitrack.player.domain.model.AudioTrack
import com.multitrack.player.domain.model.PlaybackProject
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackProjectJsonCodecTest {

    @Test
    fun `round trips project tracks bpm and loop setting`() {
        val project = PlaybackProject(
            name = "Rehearsal",
            tracks = listOf(
                AudioTrack(
                    id = "track-1",
                    name = "Drums.wav",
                    uri = "content://audio/drums",
                    durationMs = 123_000L,
                    format = AudioFormat.WAV
                ),
                AudioTrack(
                    id = "track-2",
                    name = "Bass.mp3",
                    uri = "content://audio/bass",
                    durationMs = null,
                    format = AudioFormat.MP3
                )
            ),
            bpm = 124,
            isLoopEnabled = false,
            mutedTrackIds = setOf("track-2")
        )

        assertEquals(project, PlaybackProjectJsonCodec.decode(PlaybackProjectJsonCodec.encode(project)))
    }
}

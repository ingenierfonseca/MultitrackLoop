package com.multitrack.player.data.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackClockTest {

    @Test
    fun `position grows monotonically with frames produced`() {
        val clock = PlaybackClock().apply { configure(44_100) }
        clock.start()
        repeat(10) { clock.onFramesProduced(441) } // 10 * 10 ms
        clock.publish()
        assertEquals(100L, clock.state.value.positionMs)
    }

    @Test
    fun `reset zeroes position`() {
        val clock = PlaybackClock().apply { configure(44_100) }
        clock.start()
        clock.onFramesProduced(44_100)
        clock.reset()
        clock.publish()
        assertEquals(0L, clock.state.value.positionMs)
    }

    @Test
    fun `stop marks running false`() {
        val clock = PlaybackClock()
        clock.start()
        clock.stop()
        clock.publish()
        assertEquals(false, clock.state.value.running)
    }
}

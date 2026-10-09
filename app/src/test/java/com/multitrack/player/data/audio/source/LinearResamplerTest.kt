package com.multitrack.player.data.audio.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinearResamplerTest {

    @Test
    fun `1 to 1 step copies the buffer exactly`() {
        val r = LinearResampler()
        val src = floatArrayOf(0f, 0.5f, 1f, -1f, 0.25f, -0.25f)
        val out = FloatArray(6)
        val produced = r.process(src, src.size / 2, 2, 1.0, out)
        // We must produce at least src.size/2 frames minus 1 (linear needs idx+1).
        assertTrue(produced >= 2)
        // Each produced frame should match the source.
        for (i in 0 until produced) {
            assertEquals(src[i * 2], out[i * 2], 1e-6f)
            assertEquals(src[i * 2 + 1], out[i * 2 + 1], 1e-6f)
        }
    }

    @Test
    fun `step 0_5 produces more output frames than input`() {
        val r = LinearResampler()
        val src = FloatArray(40) { it.toFloat() / 40f }
        val out = FloatArray(200)
        val produced = r.process(src, 20, 2, 0.5, out)
        assertTrue(produced > 20)
    }
}

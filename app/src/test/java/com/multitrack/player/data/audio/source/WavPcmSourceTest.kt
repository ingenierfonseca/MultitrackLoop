package com.multitrack.player.data.audio.source

import com.multitrack.player.domain.model.AudioError
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavPcmSourceTest {

    @Test
    fun `accepts 24 bit stereo PCM WAV`() {
        val data = byteArrayOf(
            0, 0, 0, 0, 0, 0,
            0, 0, 0, -1, -1, 0x7f
        )

        val source = WavPcmSource.fromStream(ByteArrayInputStream(wav(1, 2, 24, 44_100, data)))
            .getOrThrow()

        assertEquals(44_100, source.sourceSampleRate)
        assertEquals(2L, source.totalFramesAtBusRate)
    }

    @Test
    fun `accepts 32 bit float mono WAV`() {
        val data = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0x3f)

        val source = WavPcmSource.fromStream(ByteArrayInputStream(wav(3, 1, 32, 48_000, data)))
            .getOrThrow()

        assertEquals(48_000, source.sourceSampleRate)
        assertEquals(2L, source.totalFramesAtBusRate)
    }

    @Test
    fun `unsupported WAV channel layout returns an audio error`() {
        val result = WavPcmSource.fromStream(
            ByteArrayInputStream(wav(1, 3, 16, 44_100, ByteArray(12)))
        )

        assertTrue(result.exceptionOrNull() is AudioError.UnsupportedFormat)
    }

    private fun wav(
        format: Int,
        channels: Int,
        bitsPerSample: Int,
        sampleRate: Int,
        data: ByteArray
    ): ByteArray {
        val blockAlign = channels * bitsPerSample / 8
        val output = ByteArrayOutputStream()
        output.writeAscii("RIFF")
        output.writeLe32(4 + 8 + 16 + 8 + data.size)
        output.writeAscii("WAVE")
        output.writeAscii("fmt ")
        output.writeLe32(16)
        output.writeLe16(format)
        output.writeLe16(channels)
        output.writeLe32(sampleRate)
        output.writeLe32(sampleRate * blockAlign)
        output.writeLe16(blockAlign)
        output.writeLe16(bitsPerSample)
        output.writeAscii("data")
        output.writeLe32(data.size)
        output.write(data)
        return output.toByteArray()
    }

    private fun ByteArrayOutputStream.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun ByteArrayOutputStream.writeLe16(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeLe32(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }
}

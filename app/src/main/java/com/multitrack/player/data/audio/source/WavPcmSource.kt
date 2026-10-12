package com.multitrack.player.data.audio.source

import com.multitrack.player.domain.model.AudioError
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decodes common PCM and IEEE-float WAV files into the engine's stereo float source. */
object WavPcmSource {

    private const val PCM_FORMAT = 1
    private const val IEEE_FLOAT_FORMAT = 3
    private const val EXTENSIBLE_FORMAT = 0xFFFE

    fun fromStream(input: InputStream): Result<StereoFloatSource> = runCatching {
        val bytes = input.use { it.readBytes() }
        if (bytes.size < 12) throw AudioError.InvalidAudio("Cabecera WAV incompleta")

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val riff = String(bytes, 0, 4, Charsets.US_ASCII)
        val wave = String(bytes, 8, 4, Charsets.US_ASCII)
        if (riff != "RIFF" || wave != "WAVE") {
            throw AudioError.InvalidAudio("No es un archivo WAV RIFF válido")
        }

        var formatOffset = -1
        var formatSize = 0L
        var dataOffset = -1
        var dataSize = 0L
        var chunkOffset = 12L

        while (chunkOffset + 8L <= bytes.size) {
            val offset = chunkOffset.toInt()
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = uint32(buffer, offset + 4)
            val chunkDataOffset = chunkOffset + 8L
            val chunkEnd = chunkDataOffset + chunkSize
            if (chunkEnd > bytes.size) throw AudioError.InvalidAudio("Chunk WAV truncado")

            when (chunkId) {
                "fmt " -> {
                    formatOffset = chunkDataOffset.toInt()
                    formatSize = chunkSize
                }
                "data" -> if (dataOffset < 0) {
                    dataOffset = chunkDataOffset.toInt()
                    dataSize = chunkSize
                }
            }

            chunkOffset = chunkEnd + (chunkSize and 1L)
        }

        if (formatOffset < 0 || formatSize < 16L) {
            throw AudioError.InvalidAudio("Chunk 'fmt ' ausente o incompleto")
        }
        if (dataOffset < 0) throw AudioError.InvalidAudio("Chunk 'data' ausente")

        var encoding = uint16(buffer, formatOffset)
        val channels = uint16(buffer, formatOffset + 2)
        val sampleRateLong = uint32(buffer, formatOffset + 4)
        val blockAlign = uint16(buffer, formatOffset + 12)
        val bitsPerSample = uint16(buffer, formatOffset + 14)
        var validBitsPerSample = bitsPerSample

        if (encoding == EXTENSIBLE_FORMAT) {
            if (formatSize < 40L) throw AudioError.InvalidAudio("WAV extensible incompleto")
            validBitsPerSample = uint16(buffer, formatOffset + 18)
            // WAVE_FORMAT_EXTENSIBLE stores PCM (1) or IEEE float (3) in the
            // first DWORD of the sub-format GUID at offset 24.
            encoding = buffer.getInt(formatOffset + 24) and 0xFFFF
        }

        if (sampleRateLong !in 8_000L..384_000L) {
            throw AudioError.InvalidAudio("Frecuencia de muestreo WAV inválida")
        }
        if (channels !in 1..2) throw AudioError.UnsupportedFormat
        if (validBitsPerSample !in 1..bitsPerSample) throw AudioError.UnsupportedFormat

        val supportedBitDepth = when (encoding) {
            PCM_FORMAT -> bitsPerSample in setOf(8, 16, 24, 32)
            IEEE_FLOAT_FORMAT -> validBitsPerSample == bitsPerSample &&
                (bitsPerSample == 32 || bitsPerSample == 64)
            else -> false
        }
        if (!supportedBitDepth) throw AudioError.UnsupportedFormat

        val bytesPerSample = bitsPerSample / 8
        if (blockAlign < channels * bytesPerSample || blockAlign == 0) {
            throw AudioError.InvalidAudio("Alineación de bloques WAV inválida")
        }
        if (dataSize % blockAlign != 0L) throw AudioError.InvalidAudio("Datos WAV incompletos")

        val frameCountLong = dataSize / blockAlign
        if (frameCountLong <= 0L || frameCountLong > Int.MAX_VALUE / 2) {
            throw AudioError.InvalidAudio("El WAV no contiene audio reproducible")
        }
        val frameCount = frameCountLong.toInt()
        val stereo = FloatArray(Math.multiplyExact(frameCount, 2))

        for (frame in 0 until frameCount) {
            val frameOffset = dataOffset + frame * blockAlign
            val left = readSample(buffer, frameOffset, encoding, bitsPerSample, validBitsPerSample)
            val right = if (channels == 1) {
                left
            } else {
                readSample(
                    buffer,
                    frameOffset + bytesPerSample,
                    encoding,
                    bitsPerSample,
                    validBitsPerSample
                )
            }
            stereo[frame * 2] = left
            stereo[frame * 2 + 1] = right
        }

        StereoFloatSource(
            sourceSampleRate = sampleRateLong.toInt(),
            sourceChannels = 2,
            interleaved = stereo,
            totalSourceFrames = frameCountLong
        )
    }

    private fun readSample(
        buffer: ByteBuffer,
        offset: Int,
        encoding: Int,
        bitsPerSample: Int,
        validBitsPerSample: Int
    ): Float =
        when (encoding) {
            PCM_FORMAT -> when (bitsPerSample) {
                8 -> normalizePcm(
                    ((buffer.get(offset).toInt() and 0xFF) - 128) shr
                        (bitsPerSample - validBitsPerSample),
                    validBitsPerSample
                )
                16 -> normalizePcm(
                    buffer.getShort(offset).toInt() shr (bitsPerSample - validBitsPerSample),
                    validBitsPerSample
                )
                24 -> {
                    var value = (buffer.get(offset).toInt() and 0xFF) or
                        ((buffer.get(offset + 1).toInt() and 0xFF) shl 8) or
                        ((buffer.get(offset + 2).toInt() and 0xFF) shl 16)
                    if ((value and 0x800000) != 0) value = value or -0x1000000
                    normalizePcm(value shr (bitsPerSample - validBitsPerSample), validBitsPerSample)
                }
                32 -> normalizePcm(
                    buffer.getInt(offset) shr (bitsPerSample - validBitsPerSample),
                    validBitsPerSample
                )
                else -> throw AudioError.UnsupportedFormat
            }
            IEEE_FLOAT_FORMAT -> when (bitsPerSample) {
                32 -> buffer.getFloat(offset).toFiniteAudioSample()
                64 -> buffer.getDouble(offset).toFloat().toFiniteAudioSample()
                else -> throw AudioError.UnsupportedFormat
            }
            else -> throw AudioError.UnsupportedFormat
        }

    private fun normalizePcm(sample: Int, validBitsPerSample: Int): Float {
        val denominator = (1L shl (validBitsPerSample - 1)).toDouble()
        return (sample.toDouble() / denominator).toFloat().coerceIn(-1f, 1f)
    }

    private fun Float.toFiniteAudioSample(): Float =
        if (isFinite()) coerceIn(-1f, 1f) else 0f

    private fun uint16(buffer: ByteBuffer, offset: Int): Int =
        buffer.getShort(offset).toInt() and 0xFFFF

    private fun uint32(buffer: ByteBuffer, offset: Int): Long =
        buffer.getInt(offset).toLong() and 0xFFFF_FFFFL
}

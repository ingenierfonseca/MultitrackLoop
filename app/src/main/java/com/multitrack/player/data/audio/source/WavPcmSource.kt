package com.multitrack.player.data.audio.source

import com.multitrack.player.domain.model.AudioError
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WAV PCM factory. Decodes a WAV file fully into a [StereoFloatSource].
 *
 * Resampling to the bus rate happens lazily inside [StereoFloatSource.read],
 * so BPM changes apply correctly and synchronously across all sources.
 */
object WavPcmSource {

    fun fromStream(input: InputStream): Result<StereoFloatSource> = runCatching {
        val allBytes = input.readBytes()
        val bis = ByteArrayInputStream(allBytes)

        val header = ByteArray(44)
        readFully(bis, header, 0, 44)

        val riff = String(header, 0, 4, Charsets.US_ASCII)
        val wave = String(header, 8, 4, Charsets.US_ASCII)
        require(riff == "RIFF" && wave == "WAVE") {
            throw AudioError.InvalidAudio("No es un archivo WAV válido")
        }

        var pos = 12
        var sampleRate = 0
        var channels = 0
        var bitsPerSample = 0
        var audioFormat = 0
        var dataOffset = -1
        var dataSize = 0

        while (pos < allBytes.size) {
            if (allBytes.size - pos < 8) break
            val chunkId = ByteArray(4)
            val chunkSize = ByteArray(4)
            System.arraycopy(allBytes, pos, chunkId, 0, 4)
            System.arraycopy(allBytes, pos + 4, chunkSize, 0, 4)
            val size = ByteBuffer.wrap(chunkSize).order(ByteOrder.LITTLE_ENDIAN).int
            val id = String(chunkId, 0, 4, Charsets.US_ASCII)

            when (id) {
                "fmt " -> {
                    val fmt = ByteArray(size)
                    System.arraycopy(allBytes, pos + 8, fmt, 0, size)
                    val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                    audioFormat = bb.short.toInt() and 0xFFFF
                    channels = bb.short.toInt() and 0xFFFF
                    sampleRate = bb.int
                    bb.int // byte rate
                    bb.short // block align
                    bitsPerSample = bb.short.toInt() and 0xFFFF
                }
                "data" -> {
                    dataOffset = pos + 8
                    dataSize = size
                }
                else -> { /* skip */ }
            }
            pos += 8 + size
            if (size % 2 == 1) pos += 1
            if (id == "data") break
        }

        if (dataOffset < 0) throw AudioError.InvalidAudio("Chunk 'data' ausente")
        if (audioFormat != 1) throw AudioError.UnsupportedFormat
        if (bitsPerSample != 16) throw AudioError.UnsupportedFormat
        if (channels !in 1..2) throw AudioError.UnsupportedFormat
        if (dataOffset + dataSize > allBytes.size) throw AudioError.InvalidAudio("Truncated")

        val totalFrames = dataSize / (channels * (bitsPerSample / 8))

        // Convert PCM16 -> float stereo (mono duplicated).
        val stereo = FloatArray(totalFrames * 2)
        val bb = ByteBuffer.wrap(allBytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        if (channels == 2) {
            var i = 0
            while (i < totalFrames) {
                val l = bb.short.toInt()
                val r = bb.short.toInt()
                stereo[i * 2] = l.toFloat() / 32768f
                stereo[i * 2 + 1] = r.toFloat() / 32768f
                i++
            }
        } else {
            var i = 0
            while (i < totalFrames) {
                val m = bb.short.toInt().toFloat() / 32768f
                stereo[i * 2] = m
                stereo[i * 2 + 1] = m
                i++
            }
        }

        StereoFloatSource(
            sourceSampleRate = sampleRate,
            sourceChannels = 2, // already converted
            interleaved = stereo,
            totalSourceFrames = totalFrames.toLong()
        )
    }

    private fun readFully(stream: InputStream, buf: ByteArray, offset: Int, len: Int) {
        var read = 0
        while (read < len) {
            val n = stream.read(buf, offset + read, len - read)
            if (n < 0) error("EOF inesperado en WAV")
            read += n
        }
    }
}

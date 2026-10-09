package com.multitrack.player.data.audio.source

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.multitrack.player.domain.model.AudioError
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MP3 factory. Decodes MP3 to a [StereoFloatSource] using MediaCodec synchronously.
 *
 * Why synchronous on a worker thread (not the audio thread)? Because:
 *  - Decoder setup is heavy (~50–200 ms for the first frame).
 *  - Doing it on a worker thread keeps the audio callback's contract tight.
 *  - After this constructor returns, the audio thread never touches MediaCodec.
 *
 * The PCM is kept at the *source* sample rate (not the bus rate) so that
 * BPM changes are handled by the resampler at read time.
 */
object Mp3PcmSource {

    private const val TIMEOUT_US = 10_000L

    fun fromStream(input: java.io.InputStream): Result<StereoFloatSource> = runCatching {
        val tempFile = java.io.File.createTempFile("mp3decode_", ".mp3")
        tempFile.deleteOnExit()
        input.use { ins ->
            tempFile.outputStream().use { out -> ins.copyTo(out) }
        }

        val extractor = MediaExtractor()
        extractor.setDataSource(tempFile.absolutePath)
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                trackIndex = i
                format = f
                break
            }
        }
        if (trackIndex < 0 || format == null) throw AudioError.UnsupportedFormat

        extractor.selectTrack(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val info = MediaCodec.BufferInfo()
        val pcmAccumulator = ArrayList<Float>(4096)

        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIdx >= 0) {
                    val inBuf: ByteBuffer = codec.getInputBuffer(inIdx) ?: continue
                    val sz = extractor.readSampleData(inBuf, 0)
                    if (sz < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, sz, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (outIdx >= 0) {
                val outBuf = codec.getOutputBuffer(outIdx) ?: continue
                outBuf.position(info.offset)
                outBuf.limit(info.offset + info.size)
                appendPcm16LeToFloats(outBuf, channels, pcmAccumulator)
                codec.releaseOutputBuffer(outIdx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()
        tempFile.delete()

        val framesSrc = pcmAccumulator.size / channels
        val stereo = FloatArray(framesSrc * 2)
        if (channels == 2) {
            var i = 0
            while (i < framesSrc) {
                stereo[i * 2] = pcmAccumulator[i * 2]
                stereo[i * 2 + 1] = pcmAccumulator[i * 2 + 1]
                i++
            }
        } else {
            var i = 0
            while (i < framesSrc) {
                val m = pcmAccumulator[i]
                stereo[i * 2] = m
                stereo[i * 2 + 1] = m
                i++
            }
        }

        StereoFloatSource(
            sourceSampleRate = sampleRate,
            sourceChannels = 2,
            interleaved = stereo,
            totalSourceFrames = framesSrc.toLong()
        )
    }

    private fun appendPcm16LeToFloats(
        buffer: ByteBuffer,
        channels: Int,
        out: ArrayList<Float>
    ) {
        val order = buffer.order()
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        while (buffer.remaining() >= 2) {
            val s = buffer.short.toInt()
            out.add(s.toFloat() / 32768f)
            if (channels == 1) {
                out.add(out[out.size - 1])
            }
        }
        buffer.order(order)
    }
}

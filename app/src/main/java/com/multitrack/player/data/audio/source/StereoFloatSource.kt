package com.multitrack.player.data.audio.source

import com.multitrack.player.data.audio.PlaybackClock

/**
 * A pre-decoded, in-memory stereo (or mono) PCM buffer in float, kept at the
 * *original* sample rate of the source media. The Mixer converts to bus rate
 * on the fly using [LinearResampler], which honours BPM changes.
 *
 * The reason we keep the source at its original rate is precisely to make
 * speed changes deterministic: resampling step = sourceSampleRate / outputSampleRate
 * * (originalBpm / currentBpm). All tracks share the same factor → no drift.
 */
class StereoFloatSource(
    override val sourceSampleRate: Int,
    override val sourceChannels: Int,
    private val interleaved: FloatArray,
    private val totalSourceFrames: Long
) : PcmSource {

    override val totalFramesAtBusRate: Long
        get() {
            // For scheduling purposes we report the duration translated to bus rate.
            // The Mixer uses this only for the "longest track" duration estimate.
            val ratio = PlaybackClock.OUTPUT_SAMPLE_RATE.toLong() / sourceSampleRate.toLong()
            return if (ratio > 0) totalSourceFrames * ratio else totalSourceFrames
        }

    private val resampler = LinearResampler()

    private var readFrames: Long = 0L

    override fun read(out: FloatArray, speedFactor: Double): Int {
        if (readFrames >= totalSourceFrames) return 0
        val step = (sourceSampleRate.toDouble() / PlaybackClock.OUTPUT_SAMPLE_RATE.toDouble()) * speedFactor
        val produced = resampler.process(
            source = interleaved,
            sourceLen = totalSourceFrames.toInt(),
            sourceChannels = sourceChannels,
            step = step,
            out = out
        )
        readFrames = resampler.currentFrame()
        return produced
    }

    override fun seekToStart() {
        readFrames = 0L
        resampler.reset()
    }

    override fun release() { /* nothing */ }
}

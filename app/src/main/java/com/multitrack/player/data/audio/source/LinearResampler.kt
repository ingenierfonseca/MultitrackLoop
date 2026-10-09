package com.multitrack.player.data.audio.source

/**
 * Lightweight linear-interpolating resampler with channel conversion.
 *
 * Why linear and not sinc/SOLA?
 *  - Linear is O(1) per sample, allocation-free, no GC churn on the audio thread.
 *  - For multitrack mixing at 44.1 kHz with several tracks the audible aliasing
 *    is negligible because the dominant content is already band-limited and we
 *    never upsample by huge factors (we *down* sample most sources).
 *  - The class is hidden behind [PcmSource] so it can be swapped for a
 *    better algorithm later without touching the engine or the UI.
 */
internal class LinearResampler {

    /** Fractional position accumulator (in source frames). */
    private var position: Double = 0.0

    /** Last source frame kept across calls to avoid interpolation artifacts at boundaries. */
    private var lastLeft: Float = 0f
    private var lastRight: Float = 0f
    private var hasHistory: Boolean = false

    fun reset() {
        position = 0.0
        lastLeft = 0f
        lastRight = 0f
        hasHistory = false
    }

    fun currentFrame(): Long = position.toLong()

    /**
     * @param source         Interleaved stereo float, source-rate.
     * @param sourceLen      Number of source FRAMES (not samples).
     * @param sourceChannels 1 or 2 (we only support mono or stereo in this layer).
     * @param step           Source frames to advance per OUTPUT frame.
     *                       = sourceSampleRate / outputSampleRate * (originalBpm/currentBpm)
     * @param out            Output buffer, interleaved stereo float.
     * @return number of OUTPUT frames produced.
     */
    fun process(
        source: FloatArray,
        sourceLen: Int,
        sourceChannels: Int,
        step: Double,
        out: FloatArray
    ): Int {
        var outFrames = 0
        val outFramesMax = out.size / 2

        // Resolve the source as stereo.
        // Mono is duplicated; stereo is copied as-is.
        var pos = position
        var lPrev = lastLeft
        var rPrev = lastRight
        var hasPrev = hasHistory

        // If we don't yet have a "previous" frame, take the first available one.
        if (!hasPrev && sourceLen > 0) {
            lPrev = readChannel(source, 0, sourceChannels, 0)
            rPrev = if (sourceChannels == 1) lPrev else readChannel(source, 0, sourceChannels, 1)
            hasPrev = true
            // Pre-advance a tiny bit so we don't always emit the same first sample.
            if (pos < 1.0) pos = 1.0
        }

        while (outFrames < outFramesMax) {
            val idx = pos.toInt()
            if (idx >= sourceLen - 1) break // need idx and idx+1

            val frac = (pos - idx).toFloat()
            val l0 = readChannel(source, idx, sourceChannels, 0)
            val r0 = if (sourceChannels == 1) l0 else readChannel(source, idx, sourceChannels, 1)
            val l1 = readChannel(source, idx + 1, sourceChannels, 0)
            val r1 = if (sourceChannels == 1) l1 else readChannel(source, idx + 1, sourceChannels, 1)

            val l = lPrev + (l0 - lPrev) * frac
            val r = rPrev + (r0 - rPrev) * frac

            val o = outFrames * 2
            out[o] = l
            out[o + 1] = r

            // Slide history
            lPrev = l0
            rPrev = r0
            pos += step
            outFrames++

            // Safety guard against pathological step values
            if (!pos.isFinite() || pos < 0) {
                pos = 0.0
            }
        }

        // Save state for next call. The caller is expected to call reset()
        // when looping; we keep raw accumulator semantics so successive calls
        // with `source` slices continue from where we left off.
        position = pos
        lastLeft = lPrev
        lastRight = rPrev
        hasHistory = hasPrev

        return outFrames
    }

    private fun readChannel(buf: FloatArray, frame: Int, channels: Int, channel: Int): Float {
        return buf[frame * channels + channel]
    }
}
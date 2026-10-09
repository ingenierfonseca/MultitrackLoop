package com.multitrack.player.data.audio.source

/**
 * A PCM source reads float samples (interleaved stereo, normalized to [-1, 1])
 * from any underlying container (MP3, WAV, ...). It is responsible for:
 *
 *  - decoding (lazily and incrementally),
 *  - resampling to the engine's bus sample rate,
 *  - channel conversion to stereo.
 *
 * Threading: read() is called by a single audio thread. No synchronization
 * is needed internally as long as the implementation obeys single-consumer.
 *
 * BPM awareness: a source is *time-aware*. The engine passes the current BPM
 * to read() so the source can adjust its read step. This is what makes
 * speed-up / slow-down perfectly synchronized across all sources, because
 * every source uses the same factor relative to the master clock.
 */
interface PcmSource {

    /** Original sample rate of the source media. Used for diagnostics. */
    val sourceSampleRate: Int
    val sourceChannels: Int

    /**
     * Total number of PCM frames available in the source *after* resampling
     * to busSampleRate. Used to compute the mix duration and detect end-of-track.
     */
    val totalFramesAtBusRate: Long

    /**
     * Fills [out] with interleaved stereo float samples. Returns the number of
     * frames actually written (may be less than out.size / 2 if the source
     * reached EOF).
     *
     * [speedFactor] = originalBpm / currentBpm. 1.0 means nominal speed.
     * Values > 1.0 mean faster (more BPM).
     */
    fun read(out: FloatArray, speedFactor: Double): Int

    /** Rewinds to the beginning. */
    fun seekToStart()

    /** Releases decoders / native handles. */
    fun release()
}

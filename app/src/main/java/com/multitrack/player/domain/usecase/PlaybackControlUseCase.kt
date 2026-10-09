package com.multitrack.player.domain.usecase

import com.multitrack.player.domain.model.PlaybackState
import com.multitrack.player.domain.repository.PlaybackEngine

/**
 * Single use case for transport commands. Centralising them here means the
 * ViewModel never touches the engine directly and we have a single point
 * where to enforce domain rules (e.g. BPM clamping).
 *
 * Note on splitting use cases: per-command classes for trivial pass-through
 * calls add ceremony without value, so we collapse them into this facade.
 */
class PlaybackControlUseCase(
    private val engine: PlaybackEngine
) {
    fun play() = engine.play()
    fun pause() = engine.pause()
    fun stop() = engine.stop()
    fun seekToStart() = engine.seekToStart()
    fun setLoop(enabled: Boolean) = engine.setLoop(enabled)

    fun increaseBpm(current: Int): Int {
        val target = (current + 1).coerceAtMost(PlaybackState.MAX_BPM)
        engine.setBpm(target)
        return target
    }

    fun decreaseBpm(current: Int): Int {
        val target = (current - 1).coerceAtLeast(PlaybackState.MIN_BPM)
        engine.setBpm(target)
        return target
    }

    fun setBpm(bpm: Int): Int {
        val target = bpm.coerceIn(PlaybackState.MIN_BPM, PlaybackState.MAX_BPM)
        engine.setBpm(target)
        return target
    }
}

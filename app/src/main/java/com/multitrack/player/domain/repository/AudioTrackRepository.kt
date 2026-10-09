package com.multitrack.player.domain.repository

import com.multitrack.player.domain.model.AudioError
import com.multitrack.player.domain.model.AudioTrack

/**
 * Repository contract used by use cases. Implementation lives in data layer.
 */
interface AudioTrackRepository {

    /**
     * Resolves the duration of a track previously picked by the user.
     * Returns AudioError on failure (the use case translates to UI state).
     */
    suspend fun resolveTrack(uri: String, displayName: String, mime: String?): Result<AudioTrack>
}

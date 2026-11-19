package com.wanderwildwood.jimeikin.playback

import androidx.media3.common.C
import androidx.media3.session.MediaSession
import com.wanderwildwood.jimeikin.CalmMusic
import com.wanderwildwood.jimeikin.R
import com.wanderwildwood.jimeikin.data.CalmMusicDatabase
import com.wanderwildwood.jimeikin.data.NamedSongDao
import com.wanderwildwood.jimeikin.ui.SongUiModel

/**
 * What a play button pressed while Music Box is closed carries on with: on headphones, in a
 * car, or in a navigation app. The queue the screen saved last ([NowPlayingStorage]), at the
 * song and place it stopped, built the way the screen builds it ([PlaybackCoordinator]), so
 * the screen finds the same queue playing when it is opened.
 *
 * Songs only on YouTube are left out: their address has to be looked up first. A radio
 * station starts live. Null when nothing is saved or nothing in it can play.
 */
object Resumption {

    suspend fun saved(app: CalmMusic): MediaSession.MediaItemsWithStartPosition? {
        val snapshot = app.nowPlayingStorage.load() ?: return null
        val songs = NamedSongDao(CalmMusicDatabase.getDatabase(app).songDao(), app.nameOverrides)
        val queue = snapshot.queueSongIds.mapNotNull { id ->
            songs.getSongById(id)?.let { e ->
                SongUiModel(
                    id = e.id,
                    title = e.title,
                    artist = e.artist,
                    durationMillis = e.durationMillis,
                    discNumber = e.discNumber,
                    trackNumber = e.trackNumber,
                    sourceType = e.sourceType,
                    audioUri = e.audioUri,
                    album = e.album,
                )
            }
        }
        if (queue.isEmpty()) return null
        val (index, positionMs) = QueuePosition.restore(
            snapshot.queueSongIds, snapshot.currentIndex, snapshot.positionMs, queue.map { it.id },
        )
        val coordinator = PlaybackCoordinator(
            serverBitrateCeiling = { app.settingsManager.getSubsonicMaxBitrateSync() },
            unknownArtistLabel = { app.getString(R.string.library_artist_unknown) },
        )
        coordinator.rebuildPlaybackSubqueues(queue)
        val items = coordinator.localMediaItemsForQueue
        val byGlobal = coordinator.localIndexByGlobal ?: return null
        if (items.isEmpty()) return null
        // The saved song, or when it cannot play here (YouTube only), the next one that can.
        val global = (index until queue.size).firstOrNull { byGlobal[it] >= 0 } ?: return null
        val local = byGlobal[global]
        val start = when {
            global != index -> 0L
            queue[global].sourceType == "RADIO" -> C.TIME_UNSET
            else -> positionMs
        }
        return MediaSession.MediaItemsWithStartPosition(items, local, start)
    }
}

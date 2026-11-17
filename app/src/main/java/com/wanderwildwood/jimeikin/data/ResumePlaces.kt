package com.wanderwildwood.jimeikin.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLDecoder

/**
 * Where you stopped in something long, so it starts there again next time.
 *
 * Two kinds of thing keep a place, and nothing else does, so ordinary music plays exactly as
 * it always has:
 *
 * - **A long track**, 20 minutes or more: a podcast episode, a lecture, a whole album or a mix
 *   in one file. Hardly a song runs that long, and starting twenty minutes over from nothing is
 *   a real loss where a song starting over is not. Each long track keeps its own place.
 * - **Any file in a book folder**: a folder with a folder called Audiobooks, Audiobook,
 *   Podcasts, Podcast or Books above it (any case, spaces ignored), such as
 *   Music/Audiobooks/Moby Dick/. A book is often many short chapter files, so length cannot
 *   tell it from an album; where it is kept can, and that is something the reader decides and
 *   can see. The folder keeps ONE place: the file last played and where in it. Another file
 *   of the folder takes it over after 10 seconds of listening, or at once when it follows a
 *   file played to its end, so a queue passing through a chapter does not lose the book.
 *
 * A place is forgotten once its file is played to the end (or to its last 15 seconds), and a
 * long track stopped in its first 10 seconds keeps none. Kept apart from Android so it can be
 * tested; [ResumePlaceStore] keeps the places on the phone.
 */
object ResumeRules {
    /** A track this long or longer keeps its place wherever it is. */
    const val LONG_TRACK_MS = 20 * 60_000L

    /** Stopped this near the start of a long track: nothing worth keeping. */
    const val START_MS = 10_000L

    /** Stopped this near the end: it is finished, and the place is let go. */
    const val END_MS = 15_000L

    /** Most places kept; past it the one used longest ago goes. */
    const val MOST_PLACES = 200

    private const val DOCUMENT = "/document/"
    private val BOOK_FOLDER_NAMES = setOf("audiobooks", "audiobook", "podcasts", "podcast", "books")

    /**
     * The book folder [songId] sits in, or null if it is not in one. Only a song read from a
     * chosen folder knows where it is: `content://<provider>/tree/<folder>/document/<file>`,
     * the file's document id carrying its path, such as `primary:Music/Audiobooks/Book/01.mp3`.
     */
    fun bookFolderOf(songId: String): String? {
        if (!songId.startsWith("content://")) return null
        val at = songId.indexOf(DOCUMENT)
        if (at < 0) return null
        val document = runCatching { URLDecoder.decode(songId.substring(at + DOCUMENT.length), "UTF-8") }
            .getOrNull() ?: return null
        val folder = document.substringBeforeLast('/', "")
        if (folder.isEmpty()) return null
        val isBook = folder.split('/', ':').any { name ->
            name.lowercase().filterNot { it == ' ' || it == '_' || it == '-' } in BOOK_FOLDER_NAMES
        }
        return if (isBook) songId.substring(0, at) + "|" + folder else null
    }

    /** What keeps [songId]'s place: its book folder, or itself if it is long; null if neither. */
    fun keyFor(songId: String, durationMs: Long): String? {
        bookFolderOf(songId)?.let { return "folder:$it" }
        return if (durationMs >= LONG_TRACK_MS) "track:$songId" else null
    }

    /** Whether a song stopped at [positionMs] of [durationMs] has been heard to its end. */
    fun isFinished(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs >= durationMs - END_MS
}

/** One kept place: the file, where in it, and when it was kept. */
data class ResumePlace(val songId: String, val positionMs: Long, val savedAt: Long)

/**
 * The places kept, and what changes them. The player calls [record] as it plays, [finished]
 * when a file plays through, and asks [startAt] before it starts a song.
 */
class ResumePlaces(
    private val store: Store,
    private val now: () -> Long = System::currentTimeMillis,
) {
    interface Store {
        fun load(): Map<String, ResumePlace>
        fun save(key: String, place: ResumePlace?)
    }

    private val places = HashMap(store.load())

    /** Where each place was last written, so a playing track is written every few seconds. */
    private val written = HashMap<String, Long>(places.mapValues { it.value.positionMs })

    /** Where each song with a place stopped, by song id; what a song's row shows. */
    private val _marks = MutableStateFlow(marksOf())
    val marks: StateFlow<Map<String, Long>> = _marks.asStateFlow()

    /** Where to start [songId]: its kept place, or 0. */
    @Synchronized
    fun startAt(songId: String): Long =
        places.values.firstOrNull { it.songId == songId }?.positionMs ?: 0L

    /**
     * The player is at [positionMs] of [durationMs] in [songId]. [settle] says it has stopped
     * there (paused, closed): the place is written at once and the rows are told. While it plays
     * the place is written only every few seconds and the rows are left alone, so a list on the
     * e-ink panel is not redrawn every second.
     */
    @Synchronized
    fun record(songId: String, positionMs: Long, durationMs: Long, settle: Boolean) {
        if (durationMs <= 0 || positionMs < 0) return
        val key = ResumeRules.keyFor(songId, durationMs) ?: return
        val inFolder = key.startsWith("folder:")
        if (ResumeRules.isFinished(positionMs, durationMs) || (!inFolder && positionMs < ResumeRules.START_MS)) {
            forget(key)
            return
        }
        val old = places[key]
        // Another file of the book takes its place only once it has been listened to: a queue
        // or a shuffle passing through a chapter for a moment leaves the book where it was.
        // After a chapter plays through there is no place, and the next one takes it at once.
        if (inFolder && old != null && old.songId != songId && positionMs < ResumeRules.START_MS) return
        val place = ResumePlace(songId, positionMs, now())
        places[key] = place
        val moved = old == null || old.songId != songId
        val lastWritten = written[key]
        if (settle || moved || lastWritten == null || kotlin.math.abs(lastWritten - positionMs) >= WRITE_EVERY_MS) {
            store.save(key, place)
            written[key] = positionMs
            trim()
        }
        if (settle || moved) publish()
    }

    /** [songId] was played to its end: its place, if it holds one, is let go. */
    @Synchronized
    fun finished(songId: String) {
        val keys = places.filterValues { it.songId == songId }.keys.toList()
        keys.forEach { forget(it) }
    }

    /** Tells the rows where things stand now. */
    @Synchronized
    fun publish() {
        _marks.value = marksOf()
    }

    private fun forget(key: String) {
        written.remove(key)
        if (places.remove(key) != null) {
            store.save(key, null)
            publish()
        }
    }

    private fun trim() {
        while (places.size > ResumeRules.MOST_PLACES) {
            val oldest = places.minByOrNull { it.value.savedAt }?.key ?: return
            places.remove(oldest)
            written.remove(oldest)
            store.save(oldest, null)
        }
    }

    private fun marksOf(): Map<String, Long> = places.values.associate { it.songId to it.positionMs }

    companion object {
        const val WRITE_EVERY_MS = 5_000L
    }
}

/** The places, kept in the app's own preferences: one entry per place. */
class ResumePlaceStore(context: Context) : ResumePlaces.Store {
    private val prefs = context.getSharedPreferences("resume_places", Context.MODE_PRIVATE)

    override fun load(): Map<String, ResumePlace> = prefs.all.mapNotNull { (key, value) ->
        decode(value as? String ?: return@mapNotNull null)?.let { key to it }
    }.toMap()

    override fun save(key: String, place: ResumePlace?) {
        val edit = prefs.edit()
        if (place == null) edit.remove(key) else edit.putString(key, encode(place))
        edit.apply()
    }

    companion object {
        fun encode(place: ResumePlace): String = "${place.positionMs};${place.savedAt};${place.songId}"

        fun decode(text: String): ResumePlace? {
            val parts = text.split(';', limit = 3)
            if (parts.size != 3) return null
            val position = parts[0].toLongOrNull() ?: return null
            val savedAt = parts[1].toLongOrNull() ?: return null
            if (parts[2].isEmpty()) return null
            return ResumePlace(parts[2], position, savedAt)
        }
    }
}

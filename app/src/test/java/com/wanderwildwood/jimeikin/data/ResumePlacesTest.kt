package com.wanderwildwood.jimeikin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumePlacesTest {

    private val tree = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
    private fun file(path: String) = "$tree/document/" + java.net.URLEncoder.encode("primary:$path", "UTF-8").replace("+", "%20")

    private val chapter1 = file("Music/Audiobooks/Moby Dick/01 Loomings.mp3")
    private val chapter2 = file("Music/Audiobooks/Moby Dick/02 The Carpet-Bag.mp3")
    private val song = file("Music/Ada Whitlock/Quiet Hours/01 Morning.flac")
    private val lecture = file("Music/Talks/Tomas Reyes - Long Lecture.mp3")

    private val min = 60_000L

    private class MemoryStore(val saved: MutableMap<String, ResumePlace> = mutableMapOf()) : ResumePlaces.Store {
        var writes = 0
        override fun load(): Map<String, ResumePlace> = saved.toMap()
        override fun save(key: String, place: ResumePlace?) {
            writes++
            if (place == null) saved.remove(key) else saved[key] = place
        }
    }

    private var clock = 1_000L
    private fun places(store: MemoryStore = MemoryStore()) = ResumePlaces(store) { clock++ }

    @Test
    fun `a book folder is one with a folder named for books above it`() {
        assertTrue(ResumeRules.bookFolderOf(chapter1) != null)
        assertEquals(ResumeRules.bookFolderOf(chapter1), ResumeRules.bookFolderOf(chapter2))
        assertTrue(ResumeRules.bookFolderOf(file("Podcasts/Field Notes/ep 12.mp3")) != null)
        assertTrue(ResumeRules.bookFolderOf(file("Music/Audio Books/Book/1.mp3")) != null)
        assertTrue(ResumeRules.bookFolderOf(file("Music/AUDIOBOOK/Book/1.mp3")) != null)
        assertNull(ResumeRules.bookFolderOf(song))
        assertNull(ResumeRules.bookFolderOf(file("Music/The Books/Lost and Safe/01.mp3")))
        assertNull(ResumeRules.bookFolderOf("dQw4w9WgXcQ"))
        assertNull(ResumeRules.bookFolderOf("server-song-42"))
    }

    @Test
    fun `ordinary songs keep no place`() {
        val store = MemoryStore()
        val p = places(store)
        p.record(song, 2 * min, 5 * min, settle = true)
        assertEquals(0L, p.startAt(song))
        assertEquals(0, store.writes)
        assertTrue(p.marks.value.isEmpty())
    }

    @Test
    fun `a track of twenty minutes or more keeps its place`() {
        val p = places()
        p.record(lecture, 12 * min, 20 * min, settle = true)
        assertEquals(12 * min, p.startAt(lecture))
        assertEquals(mapOf(lecture to 12 * min), p.marks.value)
        val shorter = file("Music/Talks/short.mp3")
        p.record(shorter, 12 * min, 20 * min - 1, settle = true)
        assertEquals(0L, p.startAt(shorter))
    }

    @Test
    fun `a long track stopped in its first seconds or at its end keeps no place`() {
        val p = places()
        p.record(lecture, 30 * min, 60 * min, settle = true)
        p.record(lecture, 5_000L, 60 * min, settle = true)
        assertEquals(0L, p.startAt(lecture))
        p.record(lecture, 30 * min, 60 * min, settle = true)
        p.record(lecture, 60 * min - 10_000L, 60 * min, settle = true)
        assertEquals(0L, p.startAt(lecture))
        assertTrue(p.marks.value.isEmpty())
    }

    @Test
    fun `played through, a place is let go`() {
        val p = places()
        p.record(lecture, 30 * min, 60 * min, settle = true)
        p.finished(lecture)
        assertEquals(0L, p.startAt(lecture))
    }

    @Test
    fun `a book folder keeps one place, the file last played`() {
        val p = places()
        p.record(chapter1, 3 * min, 8 * min, settle = true)
        assertEquals(3 * min, p.startAt(chapter1))
        // Short chapters count too, even right at their start.
        p.record(chapter2, 2_000L, 6 * min, settle = true)
        assertEquals(0L, p.startAt(chapter1))
        assertEquals(2_000L, p.startAt(chapter2))
        assertEquals(mapOf(chapter2 to 2_000L), p.marks.value)
    }

    @Test
    fun `a chapter played through hands on to the next`() {
        val p = places()
        p.record(chapter1, 8 * min - 5_000L, 8 * min, settle = false)
        assertEquals(0L, p.startAt(chapter1))
        p.record(chapter2, 1_000L, 6 * min, settle = false)
        assertEquals(1_000L, p.startAt(chapter2))
    }

    @Test
    fun `places survive a restart`() {
        val store = MemoryStore()
        places(store).apply {
            record(lecture, 25 * min, 50 * min, settle = true)
            record(chapter2, 4 * min, 6 * min, settle = true)
        }
        val again = places(store)
        assertEquals(25 * min, again.startAt(lecture))
        assertEquals(4 * min, again.startAt(chapter2))
        assertEquals(mapOf(lecture to 25 * min, chapter2 to 4 * min), again.marks.value)
    }

    @Test
    fun `while playing a place is written every few seconds and the rows left alone`() {
        val store = MemoryStore()
        val p = places(store)
        p.record(lecture, 21 * min, 50 * min, settle = false) // new place: written and shown
        val shown = p.marks.value
        val writes = store.writes
        p.record(lecture, 21 * min + 2_000L, 50 * min, settle = false)
        assertEquals(writes, store.writes)
        assertEquals(shown, p.marks.value)
        p.record(lecture, 21 * min + 6_000L, 50 * min, settle = false)
        assertEquals(writes + 1, store.writes)
        assertEquals(shown, p.marks.value)
        p.record(lecture, 21 * min + 7_000L, 50 * min, settle = true) // paused: written and shown
        assertEquals(21 * min + 7_000L, store.saved.values.single().positionMs)
        assertEquals(21 * min + 7_000L, p.marks.value[lecture])
    }

    @Test
    fun `an unknown length keeps nothing`() {
        val p = places()
        p.record(lecture, 30 * min, -1L, settle = true)
        p.record(chapter1, 3 * min, 0L, settle = true)
        assertTrue(p.marks.value.isEmpty())
    }

    @Test
    fun `the oldest places go past the most kept`() {
        val store = MemoryStore()
        val p = places(store)
        val first = file("Music/Talks/0.mp3")
        p.record(first, 30 * min, 60 * min, settle = true)
        for (i in 1..ResumeRules.MOST_PLACES) p.record(file("Music/Talks/$i.mp3"), 30 * min, 60 * min, settle = true)
        assertEquals(ResumeRules.MOST_PLACES, store.saved.size)
        assertEquals(0L, p.startAt(first))
    }

    @Test
    fun `stored text reads back, and anything else is skipped`() {
        val place = ResumePlace("content://x/tree/a/document/b%3Bc;d", 1234L, 99L)
        assertEquals(place, ResumePlaceStore.decode(ResumePlaceStore.encode(place)))
        assertNull(ResumePlaceStore.decode("garbage"))
        assertNull(ResumePlaceStore.decode("1;2;"))
        assertNull(ResumePlaceStore.decode("x;2;id"))
    }
}

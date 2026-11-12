package com.wanderwildwood.jimeikin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Made-up songs; the tricky titles are real ones, as they are the reason for the rule. */
class FilenameTracksTest {

    private fun parsed(name: String) = FilenameTracks.parse(name)?.let { Triple(it.disc, it.track, it.title) }

    private fun titles(vararg names: String, albumSize: Int = names.size) =
        FilenameTracks.sequence(names.map { FilenameTracks.Named(it) }, albumSize)?.map { it.title }

    private val folder = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2FGlass%20Orchard%2F"

    private fun song(file: String, title: String? = null, track: Int? = null, disc: Int? = null, albumId: String? = "a1"): SongEntity {
        val id = folder + file.replace(" ", "%20")
        return SongEntity(id, title ?: file.substringBeforeLast('.'), "Mara Quill", "Glass Orchard", albumId, disc, track, null, "LOCAL_FILE", id)
    }

    @Test
    fun theWaysATrackNumberIsWrittenInAFileName() {
        assertEquals(Triple(null, 1, "Song Name"), parsed("01 Song Name"))
        assertEquals(Triple(null, 1, "Song Name"), parsed("01 - Song Name"))
        assertEquals(Triple(null, 1, "Song Name"), parsed("1. Song Name"))
        assertEquals(Triple(null, 1, "Song Name"), parsed("01_Song Name"))
        assertEquals(Triple(null, 1, "Song Name"), parsed("01-Song Name"))
        assertEquals(Triple(null, 1, "Song Name"), parsed("01.Song Name"))
        assertEquals(Triple(null, 1, "Song Name"), parsed("01 – Song Name"))
        assertEquals(Triple(null, 12, "Song Name"), parsed(" 12  Song Name "))
        assertEquals(Triple(null, 104, "Song Name"), parsed("104 Song Name"))
    }

    @Test
    fun discAndTrack() {
        assertEquals(Triple(1, 1, "Song Name"), parsed("1-01 Song Name"))
        assertEquals(Triple(2, 14, "Song Name"), parsed("2-14 - Song Name"))
        assertEquals(Triple(2, 14, "Song Name"), parsed("2-14. Song Name"))
    }

    @Test
    fun aNumberWithNothingSetOffAfterItIsNotATrackNumber() {
        assertNull(parsed("1979"))
        assertNull(parsed("01"))
        assertNull(parsed("01 - "))
        assertNull(parsed("01. "))
        assertNull(parsed("1.21 Gigawatts"))
        assertNull(parsed("100% Pure"))
        assertNull(parsed("10,000 Days"))
        assertNull(parsed("2nd Song"))
        assertNull(parsed("1000 Miles"))
        assertNull(parsed("Song 2"))
        assertNull(parsed(""))
    }

    @Test
    fun aTitleAfterTheNumberMayItselfBeginWithOne() {
        assertEquals(Triple(null, 2, "99 Problems"), parsed("02 99 Problems"))
        assertEquals(Triple(null, 5, "1979"), parsed("05. 1979"))
        assertEquals(Triple(null, 3, "7-11 Blues"), parsed("03 7-11 Blues"))
    }

    @Test
    fun zeroPaddingIsNoticed() {
        assertTrue(FilenameTracks.parse("01 Song")!!.padded)
        assertTrue(FilenameTracks.parse("1-01 Song")!!.padded)
        assertFalse(FilenameTracks.parse("1 Song")!!.padded)
        assertFalse(FilenameTracks.parse("10 Song")!!.padded)
    }

    @Test
    fun anAlbumOfNumberedNamesIsASequence() {
        assertEquals(listOf("Lantern", "Tide Clock", "Orchard Song"), titles("01 Lantern", "02 - Tide Clock", "3. Orchard Song"))
        assertEquals(listOf("Lantern", "Tide Clock"), titles("1 Lantern", "2 Tide Clock"))
        // Numbered, and a title that begins with a number of its own: only the first goes.
        assertEquals(listOf("Intro", "99 Luftballons", "7 Years"), titles("01 Intro", "02 99 Luftballons", "03 7 Years"))
    }

    @Test
    fun titlesThatBeginWithANumberAreLeftAlone() {
        // Two loose songs in one folder: 99 and 7 are not places on an album of two.
        assertNull(titles("99 Luftballons", "7 Years"))
        // One numbered-looking name among names with no number.
        assertNull(titles("7 Years", "Lantern", "Tide Clock"))
        assertNull(titles("50 Ways to Leave Your Lover", "Lantern"))
        // Two that would be the same track.
        assertNull(titles("2 Become 1", "2 Hearts", "1 Thing"))
        // One name alone says nothing.
        assertNull(titles("01 Lantern"))
        assertNull(titles("7 Years"))
        assertNull(titles())
    }

    @Test
    fun aFewFilesOfALongerAlbumAreStillTracksWhenWrittenWithZeros() {
        assertEquals(listOf("Lantern", "Tide Clock", "Orchard Song"), titles("03 Lantern", "07 Tide Clock", "09 Orchard Song"))
        // Without the zeros, nine is too high for an album of three...
        assertNull(titles("3 Lantern", "7 Tide Clock", "9 Orchard Song"))
        // ...but not when the album has more songs than these, the others titled by their tags.
        assertEquals(listOf("Lantern", "Tide Clock", "Orchard Song"), titles("3 Lantern", "7 Tide Clock", "9 Orchard Song", albumSize = 9))
    }

    @Test
    fun theSameNumberOnTwoDiscsIsTwoPlaces() {
        val twoDiscs = listOf(FilenameTracks.Named("01 Lantern", 1), FilenameTracks.Named("01 Tide Clock", 2))
        assertEquals(listOf("Lantern", "Tide Clock"), FilenameTracks.sequence(twoDiscs)?.map { it.title })
        assertEquals(listOf(1 to 1, 2 to 1), FilenameTracks.sequence(
            listOf(FilenameTracks.Named("1-01 Lantern"), FilenameTracks.Named("2-01 Tide Clock")),
        )?.map { it.disc to it.track })
    }

    @Test
    fun theFileNameIsReadFromASongsAddress() {
        assertEquals("01 Lantern", FilenameTracks.fileTitle(folder + "01%20Lantern.mp3"))
        assertEquals("Lantern + Tide", FilenameTracks.fileTitle(folder + "Lantern%20%2B%20Tide.flac"))
        // A file at the top of the card, and a folder handed over by a file manager that names by path.
        assertEquals("01 Lantern", FilenameTracks.fileTitle("content://x/tree/primary%3A/document/primary%3A01%20Lantern.mp3"))
        assertEquals("01 Lantern", FilenameTracks.fileTitle("content://x/tree/%2Fstorage%2Femulated%2F0%2FMusic/document/%2Fstorage%2Femulated%2F0%2FMusic%2F01%20Lantern.mp3"))
        assertNull(FilenameTracks.fileTitle("s1"))
        assertTrue(FilenameTracks.titleIsFileName(song("01 Lantern.mp3")))
        assertFalse(FilenameTracks.titleIsFileName(song("01 Lantern.mp3", title = "Lantern")))
    }

    @Test
    fun onlyTitlesThatCameFromFileNamesAreJudgedAndNumbered() {
        val album = listOf(
            song("01 Lantern.mp3"),
            song("02 Tide Clock.mp3"),
            // Tagged: its title is its own, whatever the file is called.
            song("03 Orchard Song.mp3", title = "99 Luftballons", track = 3),
            // No album: nothing to judge it by.
            song("04 Loose.mp3", albumId = null),
        )
        val found = FilenameTracks.inAlbums(album)
        assertEquals(setOf(album[0].id, album[1].id), found.keys)
        assertEquals("Tide Clock", found[album[1].id]?.title)

        val numbered = FilenameTracks.numbered(album[1], found[album[1].id])
        assertEquals(2, numbered.trackNumber)
        assertNull(numbered.discNumber)
        assertEquals("02 Tide Clock", numbered.title)
        assertEquals(album[3], FilenameTracks.numbered(album[3], found[album[3].id]))
    }

    @Test
    fun aTrackNumberFromTheTagsIsKeptAndADiscIsTakenFromTheName() {
        val own = song("01 Lantern.mp3", track = 9)
        assertEquals(own, FilenameTracks.numbered(own, FilenameTracks.parse("01 Lantern")))
        val disc = FilenameTracks.numbered(song("2-03 Lantern.mp3"), FilenameTracks.parse("2-03 Lantern"))
        assertEquals(3, disc.trackNumber)
        assertEquals(2, disc.discNumber)
        // A disc the song already has (its folder was "CD 1") stays.
        val known = FilenameTracks.numbered(song("2-03 Lantern.mp3", disc = 1), FilenameTracks.parse("2-03 Lantern"))
        assertEquals(1, known.discNumber)
    }

    @Test
    fun anAlbumOfUnnumberedNamesIsUntouched() {
        val album = listOf(song("99 Luftballons.mp3"), song("7 Years.mp3"), song("Lantern.mp3"))
        assertTrue(FilenameTracks.inAlbums(album).isEmpty())
        assertTrue(FilenameTracks.inAlbums(listOf(song("99 Luftballons.mp3"), song("7 Years.mp3"))).isEmpty())
    }
}

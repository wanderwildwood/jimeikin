package com.wanderwildwood.jimeikin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicFolderSongsTest {

    private val provider = "content://com.android.externalstorage.documents"

    /** A folder chosen with Android's own picker, as it writes the address. */
    private fun folder(id: String) = "$provider/tree/${enc(id)}"

    /** A song read from [folderId], as the scan writes its address. */
    private fun song(folderId: String, fileId: String) = "${folder(folderId)}/document/${enc(fileId)}"

    private fun enc(s: String) = s.replace("%", "%25").replace(":", "%3A").replace("/", "%2F").replace(" ", "%20")

    private val oldCard = folder("1A2B-3C4D:Music")
    private val newCard = folder("5E6F-7A8B:Music")
    private val phone = folder("primary:Music")
    private val lantern = song("1A2B-3C4D:Music", "1A2B-3C4D:Music/Mara Quill/Lantern.mp3")

    @Test
    fun aSongIsFromTheFolderWhoseAddressItCarries() {
        assertTrue(MusicFolderSongs.isFrom(lantern, oldCard))
        assertFalse(MusicFolderSongs.isFrom(lantern, newCard))
        // A folder whose name begins the same way is another folder.
        assertFalse(MusicFolderSongs.isFrom(lantern, folder("1A2B-3C4D:Mus")))
        assertEquals(oldCard, MusicFolderSongs.treeOf(lantern))
        assertEquals(oldCard, MusicFolderSongs.folderOf(lantern, setOf(phone, oldCard)))
    }

    @Test
    fun songsNotReadFromAChosenFolderHaveNone() {
        assertNull(MusicFolderSongs.treeOf("file:///storage/emulated/0/Music/Music%20Box/a.m4a"))
        assertNull(MusicFolderSongs.treeOf("dQw4w9WgXcQ"))
        assertNull(MusicFolderSongs.treeOf("subsonic:123"))
    }

    @Test
    fun onlySongsOfAnUnreadableFolderAreHidden() {
        val ok = song("primary:Music", "primary:Music/Tomas Reyes/Harbor.mp3")
        assertTrue(MusicFolderSongs.isHidden(lantern, setOf(oldCard)))
        assertFalse(MusicFolderSongs.isHidden(ok, setOf(oldCard)))
        assertFalse(MusicFolderSongs.isHidden(lantern, emptySet()))
    }

    @Test
    fun everythingIsOfferedWhileEveryFolderReads() {
        // Even a song of a folder let go: the scan that follows a whole read deletes it.
        assertTrue(MusicFolderSongs.isOffered(lantern, true, setOf(phone), emptySet()))
    }

    @Test
    fun whileAFolderCannotBeReadItsSongsAndThoseOfFoldersLetGoAreNotOffered() {
        assertFalse(MusicFolderSongs.isOffered(lantern, true, setOf(oldCard), setOf(oldCard)))
        // The old card let go, the new one chosen, a third folder out: nothing was deleted,
        // and the old card's songs are not offered all the same.
        assertFalse(MusicFolderSongs.isOffered(lantern, true, setOf(newCard, phone), setOf(phone)))
        // A download is not a song read off the card, and is offered.
        assertTrue(MusicFolderSongs.isOffered(lantern, false, setOf(newCard, phone), setOf(phone)))
        val streamed = "dQw4w9WgXcQ"
        assertTrue(MusicFolderSongs.isOffered(streamed, false, setOf(phone), setOf(phone)))
    }

    @Test
    fun aFolderIsGoneOnlyWhenTheSongIsFromOneAndItCannotBeRead() {
        assertTrue(MusicFolderSongs.folderGone(lantern, songTreeReadable = false))
        assertFalse(MusicFolderSongs.folderGone(lantern, songTreeReadable = true))
        assertFalse(MusicFolderSongs.folderGone("dQw4w9WgXcQ", songTreeReadable = null))
        assertFalse(MusicFolderSongs.folderGone(null, songTreeReadable = false))
    }

    @Test
    fun replacingAFolderLetsTheOldOneGo() {
        assertEquals(setOf(phone, newCard), MusicFolderSongs.replace(setOf(phone, oldCard), oldCard, newCard))
        assertEquals(setOf(phone, oldCard), MusicFolderSongs.replace(setOf(phone, oldCard), oldCard, oldCard))
    }

    @Test
    fun aSongsPlaceIsItsPathOnTheVolume() {
        assertEquals("Music/Mara Quill/Lantern.mp3", MusicFolderSongs.placeOf(lantern))
        // The whole card chosen rather than its Music folder: the same place.
        assertEquals(
            "Music/Mara Quill/Lantern.mp3",
            MusicFolderSongs.placeOf(song("5E6F-7A8B:", "5E6F-7A8B:Music/Mara Quill/Lantern.mp3")),
        )
        // Another app's provider, with ids that are paths: below the chosen folder.
        val other = "content://org.example.files/tree/%2Froot%2FTunes/document/%2Froot%2FTunes%2FA%2Fb.mp3"
        assertEquals("A/b.mp3", MusicFolderSongs.placeOf(other))
        assertNull(MusicFolderSongs.placeOf("dQw4w9WgXcQ"))
    }

    @Test
    fun songsFoundAgainInTheSamePlaceOnANewCardAreCarriedOver() {
        val harbor = song("1A2B-3C4D:Music", "1A2B-3C4D:Music/Tomas Reyes/Harbor.flac")
        val newLantern = song("5E6F-7A8B:Music", "5E6F-7A8B:Music/Mara Quill/Lantern.mp3")
        val newHarbor = song("5E6F-7A8B:Music", "5E6F-7A8B:Music/Tomas Reyes/Harbor.flac")
        val newOnly = song("5E6F-7A8B:Music", "5E6F-7A8B:Music/Ada Whitlock/Orchard.mp3")
        assertEquals(
            mapOf(lantern to newLantern, harbor to newHarbor),
            MusicFolderSongs.carryOver(listOf(lantern, harbor), listOf(newLantern, newHarbor, newOnly)),
        )
    }

    @Test
    fun aFolderChosenThroughFilesIsMatchedByItsPathOnTheVolume() {
        val files = "content://com.wanderwildwood.tana.picked"
        val old = "$files/tree/%2Fstorage%2F1A2B-3C4D%2FMusic/document/%2Fstorage%2F1A2B-3C4D%2FMusic%2FMara%20Quill%2FLantern.mp3"
        val new = "$files/tree/%2Fstorage%2F5E6F-7A8B/document/%2Fstorage%2F5E6F-7A8B%2FMusic%2FMara%20Quill%2FLantern.mp3"
        assertEquals("Music/Mara Quill/Lantern.mp3", MusicFolderSongs.placeOf(old))
        assertEquals("Music/Mara Quill/Lantern.mp3", MusicFolderSongs.placeOf(new))
        assertEquals(mapOf(old to new), MusicFolderSongs.carryOver(listOf(old), listOf(new)))
        // Chosen once with Android's own picker and again through Files: still the same place.
        val ownPicker = song("5E6F-7A8B:Music", "5E6F-7A8B:Music/Mara Quill/Lantern.mp3")
        assertEquals(mapOf(old to ownPicker), MusicFolderSongs.carryOver(listOf(old), listOf(ownPicker)))
        val phone = "$files/tree/%2Fstorage%2Femulated%2F0%2FMusic/document/%2Fstorage%2Femulated%2F0%2FMusic%2FA.mp3"
        assertEquals("Music/A.mp3", MusicFolderSongs.placeOf(phone))
    }

    @Test
    fun aPlaceFoundTwiceCarriesNothingOver() {
        // The new card chosen twice, as itself and as its Music folder: two songs at one place.
        val a = song("5E6F-7A8B:Music", "5E6F-7A8B:Music/Mara Quill/Lantern.mp3")
        val b = song("5E6F-7A8B:", "5E6F-7A8B:Music/Mara Quill/Lantern.mp3")
        assertEquals(emptyMap<String, String>(), MusicFolderSongs.carryOver(listOf(lantern), listOf(a, b)))
    }

    @Test
    fun aSongThatIsNotThereIsNotCarriedOver() {
        val elsewhere = song("5E6F-7A8B:Music", "5E6F-7A8B:Music/Mara Quill/Lantern (live).mp3")
        assertEquals(emptyMap<String, String>(), MusicFolderSongs.carryOver(listOf(lantern), listOf(elsewhere)))
        assertEquals(emptyMap<String, String>(), MusicFolderSongs.carryOver(emptyList(), listOf(elsewhere)))
    }
}

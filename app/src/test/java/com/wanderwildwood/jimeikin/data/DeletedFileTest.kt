package com.wanderwildwood.jimeikin.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletedFileTest {

    private val tree = "primary:Music"
    private val song = "primary:Music/Ada Whitlock/Field Songs/01 Morning.mp3"
    private val album = "primary:Music/Ada Whitlock/Field Songs"
    private val artist = "primary:Music/Ada Whitlock"

    @Test
    fun aDeleteTheProviderDidIsGone() {
        assertTrue(DeletedFile.isGone(true, tree, song) { error("not asked") })
    }

    @Test
    fun aFileItsFolderNoLongerListsIsAlreadyGone() {
        // Removed in a file manager: the folder answers, without it.
        assertTrue(DeletedFile.isGone(false, tree, song) { folder ->
            if (folder == album) listOf("$album/02 Noon.mp3") else error("not asked")
        })
        // The folder answers and is empty now.
        assertTrue(DeletedFile.isGone(false, tree, song) { folder ->
            if (folder == album) emptyList() else error("not asked")
        })
    }

    @Test
    fun aFileWhoseFolderWentWithItIsAlreadyGone() {
        // The whole album folder removed: it gives no answer, the folder above leaves it out.
        assertTrue(DeletedFile.isGone(false, tree, song) { folder ->
            when (folder) {
                album -> null
                artist -> listOf("$artist/Other Songs")
                else -> error("not asked")
            }
        })
    }

    @Test
    fun aFileStillListedStays() {
        assertFalse(DeletedFile.isGone(false, tree, song) { listOf(song) })
    }

    @Test
    fun aFileThatCouldNotBeReachedStays() {
        // A folder whose permission was dropped, or a provider that fails.
        assertFalse(DeletedFile.isGone(false, tree, song) { throw SecurityException("no permission") })
        assertFalse(DeletedFile.isGone(false, tree, song) { throw IllegalStateException("provider error") })
        // No answer anywhere: a card out of the phone answers like this, and the file is on it.
        assertFalse(DeletedFile.isGone(false, tree, song) { null })
    }

    @Test
    fun aFolderThatStillListsTheMissingFolderKeepsTheRow() {
        // The album folder will not answer but is still there: nothing says the file is gone.
        assertFalse(DeletedFile.isGone(false, tree, song) { folder ->
            when (folder) {
                album -> null
                artist -> listOf(album)
                else -> error("not asked")
            }
        })
    }

    @Test
    fun nothingAboveTheChosenFolderIsAsked() {
        val asked = mutableListOf<String>()
        assertFalse(DeletedFile.isGone(false, tree, song) { folder -> asked.add(folder); null })
        assertTrue(asked == listOf(album, artist, tree))
    }

    @Test
    fun anIdThatNamesNoFolderStays() {
        // A provider whose ids are not paths, or a file outside the chosen folder.
        assertFalse(DeletedFile.isGone(false, "acc=1;doc=7", "acc=1;doc=42") { emptyList() })
        assertFalse(DeletedFile.isGone(false, tree, "primary:Podcasts/Tomas Reyes/1.mp3") { emptyList() })
        assertFalse(DeletedFile.isGone(false, null, song) { emptyList() })
        assertFalse(DeletedFile.isGone(false, tree, null) { emptyList() })
    }
}

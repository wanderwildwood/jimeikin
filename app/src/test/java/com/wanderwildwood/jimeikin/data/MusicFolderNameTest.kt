package com.wanderwildwood.jimeikin.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicFolderNameTest {

    private fun own(id: String) = MusicFolderName.of(id, true, null, "Phone", "Card")
    private fun other(id: String, name: String?) = MusicFolderName.of(id, false, name, "Phone", "Card")

    @Test
    fun aFolderFromAndroidsPickerReadsAsWhereItIs() {
        assertEquals("Phone > Music", own("primary:Music"))
        assertEquals("Phone > Music > Albums", own("primary:Music/Albums"))
        assertEquals("Phone", own("primary:"))
        assertEquals("Card > Music", own("1A2B-3C4D:Music"))
    }

    @Test
    fun aFolderFromAnotherFileManagerGoesByTheNameThatAppGivesIt() {
        assertEquals("Albums", other("/storage/emulated/0/Music/Albums", "Albums"))
        assertEquals("My music", other("/storage/emulated/0/Music", " My music "))
    }

    @Test
    fun withNoNameGivenItIsTheLastPartOfTheId() {
        assertEquals("Albums", other("/storage/emulated/0/Music/Albums", null))
        assertEquals("Albums", other("/storage/emulated/0/Music/Albums/", ""))
        assertEquals("Albums", other("primary:Music/Albums", null))
        assertEquals("Music", other("1A2B-3C4D:Music", "  "))
        // An id that is not a path is still not an address.
        assertEquals("folder-12", other("folder-12", null))
    }

    @Test
    fun anIdThatIsNotAndroidsShapeStillGetsAName() {
        assertEquals("Albums", own("/storage/emulated/0/Music/Albums"))
    }
}

package com.wanderwildwood.jimeikin

import com.wanderwildwood.jimeikin.data.LegacyKinds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rows and ids stored before 2.18.14 under the old kind names read as the new ones. */
class LegacyKindsTest {
    @Test
    fun oldKindsMapToTheNewOnes() {
        assertEquals("ONLINE_SEARCH", LegacyKinds.current("YOUTUBE"))
        assertEquals("ONLINE_SEARCH_DOWNLOAD", LegacyKinds.current("YOUTUBE_DOWNLOAD"))
        for (k in listOf("LOCAL_FILE", "SUBSONIC", "SUBSONIC_DOWNLOAD", "RADIO", "ONLINE_SEARCH", "ONLINE_SEARCH_DOWNLOAD")) {
            assertEquals(k, LegacyKinds.current(k))
        }
    }

    @Test
    fun oldIdPrefixesMapToTheNewOnes() {
        assertEquals("ONLINE_SEARCH:mara quill", LegacyKinds.currentId("YOUTUBE:mara quill"))
        assertEquals("ONLINE_SEARCH_DOWNLOAD:mara:lantern", LegacyKinds.currentId("YOUTUBE_DOWNLOAD:mara:lantern"))
        assertEquals("LOCAL_FILE:mara", LegacyKinds.currentId("LOCAL_FILE:mara"))
        assertEquals("vid00000001", LegacyKinds.currentId("vid00000001"))
    }

    @Test
    fun theMigrationCoversEveryTableAndColumnThatHoldsAKind() {
        val sql = LegacyKinds.statements.joinToString("\n")
        for (t in listOf("songs", "albums", "artists", "hearted_songs")) {
            assertTrue(t, sql.contains("UPDATE $t SET sourceType = 'ONLINE_SEARCH_DOWNLOAD' WHERE sourceType = 'YOUTUBE_DOWNLOAD'"))
            assertTrue(t, sql.contains("UPDATE $t SET sourceType = 'ONLINE_SEARCH' WHERE sourceType = 'YOUTUBE'"))
        }
        for (c in listOf("artists SET id", "albums SET id", "albums SET artistId", "songs SET albumId", "songs SET artistId", "name_overrides SET targetId")) {
            assertTrue(c, sql.contains("UPDATE $c = 'ONLINE_SEARCH:'"))
            assertTrue(c, sql.contains("UPDATE $c = 'ONLINE_SEARCH_DOWNLOAD:'"))
        }
        // Downloads are rewritten before plain kinds, which they begin with.
        val firstDownload = LegacyKinds.statements.indexOfFirst { it.contains("artists SET id = 'ONLINE_SEARCH_DOWNLOAD:'") }
        val firstPlain = LegacyKinds.statements.indexOfFirst { it.contains("artists SET id = 'ONLINE_SEARCH:'") }
        assertTrue(firstDownload < firstPlain)
    }
}

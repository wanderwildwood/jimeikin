package com.wanderwildwood.jimeikin.ui

import com.wanderwildwood.jimeikin.data.ArtistNames
import org.junit.Assert.assertEquals
import org.junit.Test

class RowArtistTest {

    private fun row(artist: String, known: String?, local: Boolean = false) =
        rowArtist(artist, known, local, localFileLabel = "Lokale Datei", unknownArtistLabel = "Unbekannter Künstler")

    @Test
    fun theUnknownArtistPageLeavesTheNameOffItsRowsInAnyLanguage() {
        assertEquals("", row(ArtistNames.UNKNOWN, ArtistNames.UNKNOWN))
    }

    @Test
    fun anUnknownArtistElsewhereIsShownInThePhonesLanguage() {
        assertEquals("Unbekannter Künstler", row(ArtistNames.UNKNOWN, null))
        assertEquals("Unbekannter Künstler", row(ArtistNames.UNKNOWN, "Ada Whitlock"))
    }

    @Test
    fun aNamedArtistIsLeftOffOnlyOnItsOwnPage() {
        assertEquals("", row("Björk", "Bjork"))
        assertEquals("Björk", row("Björk", "Tomas Reyes"))
    }

    @Test
    fun aLocalFileWithNoArtistSaysSo() {
        assertEquals("Lokale Datei", row("", null, local = true))
        assertEquals("", row("", null, local = false))
    }
}

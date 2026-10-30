package com.wanderwildwood.jimeikin.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuHintTest {
    @Test
    fun showsOnAListOfSongsUntilASongHasBeenHeld() {
        assertTrue(menuHintVisible(heldBefore = false, hasSongs = true))
    }

    @Test
    fun neverShowsAfterASongHasBeenHeld() {
        assertFalse(menuHintVisible(heldBefore = true, hasSongs = true))
    }

    @Test
    fun doesNotShowOverAnEmptyList() {
        assertFalse(menuHintVisible(heldBefore = false, hasSongs = false))
    }
}

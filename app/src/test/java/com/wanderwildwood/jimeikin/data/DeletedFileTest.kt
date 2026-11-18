package com.wanderwildwood.jimeikin.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletedFileTest {

    @Test
    fun aDeleteTheProviderDidIsGone() {
        assertTrue(DeletedFile.isGone(true) { error("not asked") })
    }

    @Test
    fun aFileTheProviderAnswersForWithNoRowIsAlreadyGone() {
        assertTrue(DeletedFile.isGone(false) { 0 })
    }

    @Test
    fun aFileStillListedStays() {
        assertFalse(DeletedFile.isGone(false) { 1 })
    }

    @Test
    fun aFileThatCouldNotBeReachedStays() {
        // A folder whose permission was dropped, or a provider that fails.
        assertFalse(DeletedFile.isGone(false) { throw SecurityException("no permission") })
        assertFalse(DeletedFile.isGone(false) { throw IllegalStateException("provider error") })
        // No answer at all: a card out of the phone answers like this, and the file is on it.
        assertFalse(DeletedFile.isGone(false) { null })
    }
}

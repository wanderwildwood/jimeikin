package com.wanderwildwood.jimeikin

import org.junit.Assert.assertEquals
import org.junit.Test

/** Music Box (the full app) keeps its own id and folder, apart from Music Box K. */
class MusicBoxIdentityTest {
    @Test
    fun itKeepsItsIdentity() {
        assertEquals("com.wanderwildwood.jimeikin", BuildConfig.APPLICATION_ID)
        assertEquals("Music Box", BuildConfig.DOWNLOAD_FOLDER)
        assertEquals("2.18.16", BuildConfig.VERSION_NAME)
    }
}

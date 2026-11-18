package com.wanderwildwood.jimeikin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Music Box (the full app) keeps its own id and folder, apart from Music Box K. */
class MusicBoxIdentityTest {
    @Test
    fun itKeepsItsIdentity() {
        assertEquals("com.wanderwildwood.jimeikin", BuildConfig.APPLICATION_ID)
        assertEquals("Music Box", BuildConfig.DOWNLOAD_FOLDER)
        assertTrue(Regex("""\d+\.\d+\.\d+""").matches(BuildConfig.VERSION_NAME))
    }

    @Test
    fun thePrivacyPolicyNamesThisVersion() {
        // Stamped in the release commit: the published policy names the version it ships with.
        val policy = File("../PRIVACY.md").readText()
        assertTrue("PRIVACY.md is not stamped ${BuildConfig.VERSION_NAME}", policy.contains("Version ${BuildConfig.VERSION_NAME}."))
    }
}

package com.wanderwildwood.jimeikin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Music Box K is an app of its own: its identity, and nothing of YouTube anywhere in it. */
class MusicBoxKTest {

    private val res = File("src")

    @Test
    fun itHasItsOwnIdentity() {
        assertEquals("com.wanderwildwood.jimeikin.k", BuildConfig.APPLICATION_ID)
        assertEquals("1.0.1", BuildConfig.VERSION_NAME)
        assertEquals(1, BuildConfig.VERSION_CODE)
        assertEquals("Music Box K", BuildConfig.DOWNLOAD_FOLDER)
    }

    @Test
    fun noYouTubeCodeIsOnTheClasspath() {
        for (name in listOf(
            "org.schabi.newpipe.extractor.NewPipe", "org.jsoup.Jsoup", "org.mozilla.javascript.Context",
            "com.wanderwildwood.jimeikin.YouTubeDownload", "com.wanderwildwood.jimeikin.YouTubeMusicInnertubeClient",
        )) {
            assertTrue(name, runCatching { Class.forName(name) }.isFailure)
        }
    }

    @Test
    fun noYouTubeWordsInTheStoreEditionsResources() {
        // Values only (comments are not compiled into the APK), in the files this app is built from.
        val files = listOf("main/res", "store/res").map { File(res, it) }.flatMap { it.walkTopDown().filter(File::isFile).toList() }
            .filter { it.extension == "xml" && "values" in it.path }
        assertTrue(files.isNotEmpty())
        for (f in files) {
            val text = f.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            assertFalse(f.path, Regex("youtube|newpipe|innertube", RegexOption.IGNORE_CASE).containsMatchIn(text))
        }
    }

    @Test
    fun everyStringThatNamesMusicBoxNamesMusicBoxK() {
        val own = File(res, "store/res/values/strings.xml").readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        for (dir in listOf("values", "values-de", "values-fr")) {
            val main = File(res, "main/res/$dir/strings.xml").readText()
            val brand = File(res, "store/res/$dir/brand.xml").readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            val names = Regex("""<string name="([^"]+)"[^>]*>([^<]*Music Box[^<]*)</string>""").findAll(main)
                .filter { !it.value.contains("translatable=\"false\"") }.map { it.groupValues[1] }.toList()
            assertTrue(dir, names.isNotEmpty())
            for (n in names) {
                assertTrue("$dir/$n has no K twin (run tools/brand-k.py)", brand.contains("name=\"$n\"") || (dir == "values" && own.contains("name=\"$n\"")))
            }
            // Nothing in the K strings says plain "Music Box" without the K.
            assertFalse(dir, Regex("Music Box(?! K)").containsMatchIn(brand))
        }
        assertFalse(Regex("Music Box(?! K)").containsMatchIn(own))
    }

    @Test
    fun noCodeOrStringInMusicBoxKSourcesNamesYouTube() {
        val sources = listOf("main/java", "store/java").map { File(res, it) }.flatMap { it.walkTopDown().filter { f -> f.extension == "kt" }.toList() }
        assertTrue(sources.isNotEmpty())
        for (f in sources) f.readLines().forEachIndexed { i, line ->
            val t = line.trim()
            if (t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")) return@forEachIndexed
            val code = t.substringBefore("//")
            assertFalse("${f.path}:${i + 1}", Regex("youtube|newpipe|schabi", RegexOption.IGNORE_CASE).containsMatchIn(code))
        }
    }
}

package com.wanderwildwood.jimeikin.data

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * The half of a tag edit that needs no phone: the fields written into a working copy and read
 * back, over one small file of each kind (src/test/resources/tags, under half a second of
 * tone each), and the search for the folder a file is in.
 */
class TagEditorTest {

    private val all = TagEditor.Fields(
        title = "Dawn Chorus",
        artist = "Tomas Reyes",
        album = "Low Tide",
        albumArtist = "Tomas Reyes",
        trackNumber = "7",
    )

    private fun fixture(name: String): File {
        val temp = File.createTempFile("tag_test", "." + name.substringAfterLast('.'))
        temp.deleteOnExit()
        javaClass.getResourceAsStream("/tags/$name")!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
        return temp
    }

    /** An MP3 whose only tag is the old 128-byte one at its end, track 3. */
    private fun id3v1(): File {
        val temp = fixture("bare.mp3")
        val tag = ByteArray(128)
        fun put(s: String, at: Int) = s.toByteArray(Charsets.ISO_8859_1).copyInto(tag, at)
        put("TAG", 0)
        put("First Light", 3)
        put("Ada Whitlock", 33)
        put("Paper Lanterns", 63)
        tag[126] = 3
        tag[127] = 255.toByte()
        temp.appendBytes(tag)
        return temp
    }

    /** Title, artist, album, album artist and track as the file now has them. */
    private fun read(file: File): String {
        val tag = AudioFileIO.read(file).tag
        return listOf(FieldKey.TITLE, FieldKey.ARTIST, FieldKey.ALBUM, FieldKey.ALBUM_ARTIST, FieldKey.TRACK)
            .joinToString("|") { tag.getFirst(it) }
    }

    private val tagged = listOf("id3v23.mp3", "id3v24.mp3", "both.mp3", "aac.m4a", "alac.m4a", "vorbis.flac", "vorbis.ogg")
    private val untagged = listOf("bare.mp3", "bare.m4a", "plain.wav")

    @Test
    fun everyFieldIsWrittenIntoEveryKindOfFile() {
        for (name in tagged + untagged) {
            val file = fixture(name)
            assertNull(name, TagEditor.editFile(file, all))
            assertEquals(name, "Dawn Chorus|Tomas Reyes|Low Tide|Tomas Reyes|7", read(file))
        }
    }

    @Test
    fun oneFieldChangedLeavesTheOthersAsTheyWere() {
        for (name in tagged) {
            val file = fixture(name)
            assertNull(name, TagEditor.editFile(file, TagEditor.Fields(trackNumber = "7")))
            assertEquals(name, "First Light|Ada Whitlock|Paper Lanterns||7", read(file))
        }
        for (name in untagged) {
            val file = fixture(name)
            assertNull(name, TagEditor.editFile(file, TagEditor.Fields(albumArtist = "Tomas Reyes")))
            assertEquals(name, "|||Tomas Reyes|", read(file))
        }
    }

    @Test
    fun aTrackNumberTakenAwayLeavesNoneBehind() {
        // id3v23.mp3, aac.m4a and vorbis.flac say "1/16", "1/16" and "4/16".
        for (name in tagged) {
            val file = fixture(name)
            assertNull(name, TagEditor.editFile(file, TagEditor.Fields(trackNumber = "")))
            assertEquals(name, "First Light|Ada Whitlock|Paper Lanterns||", read(file))
        }
    }

    @Test
    fun anMp3WithOnlyTheOldTagKeepsWhatItSaidWhenOneFieldIsEdited() {
        val file = id3v1()
        assertNull(TagEditor.editFile(file, TagEditor.Fields(albumArtist = "Ada Whitlock")))
        assertEquals("First Light|Ada Whitlock|Paper Lanterns|Ada Whitlock|3", read(file))
    }

    @Test
    fun anMp3WithOnlyTheOldTagTakesATitleLongerThanThatTagHolds() {
        val file = id3v1()
        val long = "A Title That Runs Well Past Thirty Letters Long"
        assertNull(TagEditor.editFile(file, TagEditor.Fields(title = long)))
        assertEquals("$long|Ada Whitlock|Paper Lanterns||3", read(file))
    }

    @Test
    fun aKindThatCannotBeEditedSaysSoAndIsLeftAlone() {
        val file = fixture("opus.opus")
        val before = file.readBytes()
        assertEquals(TagEditor.Result.Unsupported, TagEditor.editFile(file, all))
        assertEquals(before.toList(), file.readBytes().toList())
    }

    // The folders of a chosen tree, as a provider lists them: id to is-a-folder.
    private fun tree(vararg folders: Pair<String, List<Pair<String, Boolean>>>): (String) -> List<Pair<String, Boolean>>? {
        val map = folders.toMap()
        return { map[it] }
    }

    @Test
    fun theFolderIsReadOffAnIdThatIsAPath() {
        var asked = 0
        val children = tree("/music/Ada/Lanterns" to listOf("/music/Ada/Lanterns/01.mp3" to false))
        val parent = TagEditor.findParent("/music", "/music/Ada/Lanterns/01.mp3") { asked++; children(it) }
        assertEquals("/music/Ada/Lanterns", parent)
        assertEquals(1, asked)
    }

    @Test
    fun theFolderIsFoundByLookingWhenTheIdSaysNothing() {
        val children = tree(
            "root" to listOf("a" to true, "b" to true, "17" to false),
            "a" to listOf("21" to false),
            "b" to listOf("c" to true),
            "c" to listOf("42" to false),
        )
        assertEquals("c", TagEditor.findParent("root", "42", children))
        assertEquals("root", TagEditor.findParent("root", "17", children))
    }

    @Test
    fun aFileAtTheTopOfACardIsFoundThere() {
        // The system's own ids: "primary:song.mp3" has no slash to cut at.
        val children = tree("primary:" to listOf("primary:Music" to true, "primary:song.mp3" to false), "primary:Music" to emptyList())
        assertEquals("primary:", TagEditor.findParent("primary:", "primary:song.mp3", children))
    }

    @Test
    fun aFileThatIsNotUnderTheChosenFolderHasNoFolder() {
        val children = tree("root" to listOf("a" to true), "a" to listOf("a" to true, "21" to false))
        assertNull(TagEditor.findParent("root", "elsewhere/42", children))
    }
}

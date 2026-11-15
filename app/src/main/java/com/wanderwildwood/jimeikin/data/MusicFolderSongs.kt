package com.wanderwildwood.jimeikin.data

import java.net.URLDecoder

/**
 * Which chosen music folder a song came from, and what becomes of it when that folder cannot be
 * read: a memory card taken out, swapped or reformatted, or a folder's permission gone.
 *
 * Kept apart from Android so it can be tested. A song read from a chosen folder is known by its
 * tree address, which carries the folder's own address at its front:
 * folder `content://<provider>/tree/<folder id>`, song `content://<provider>/tree/<folder id>/document/<file id>`.
 *
 * Nothing here deletes anything. A folder that cannot be read hides its songs; they are still
 * in the library, with their hearts and playlist places, and come back when it can be read again
 * (a card can be taken out and put back). Only when the reader chooses another folder in its
 * place, and the old one is let go, do its songs leave: then each one whose file is found again
 * at the same place in the new folder hands its hearts, playlist places and given name over.
 */
object MusicFolderSongs {

    private const val DOCUMENT = "/document/"
    private const val TREE = "/tree/"
    private val STORAGE_PATH = Regex("""/storage/(?:emulated/\d+|[^/]+)/(.+)""")

    /** Whether [songId] was read from the chosen folder [folder]. */
    fun isFrom(songId: String, folder: String): Boolean =
        songId.startsWith(folder.trimEnd('/') + DOCUMENT)

    /** The folder address a song read from a chosen folder carries, or null for any other song. */
    fun treeOf(songId: String): String? {
        if (!songId.startsWith("content://")) return null
        val tree = songId.indexOf(TREE)
        val document = songId.indexOf(DOCUMENT)
        if (tree < 0 || document < tree) return null
        return songId.substring(0, document)
    }

    /** Whether [songId] came from one of the folders that cannot be read now, and so is not offered. */
    fun isHidden(songId: String, unreadable: Set<String>): Boolean =
        unreadable.isNotEmpty() && unreadable.any { isFrom(songId, it) }

    /**
     * Whether a song is offered in the library. Not one from a folder that cannot be read now;
     * nor, while any folder cannot be read, a song read off the card ([readFromFolder]) from a
     * folder no longer chosen: a scan that cannot read every folder deletes nothing, so a folder
     * let go in the meantime leaves its songs in the library until the next whole scan.
     */
    fun isOffered(songId: String, readFromFolder: Boolean, folders: Set<String>, unreadable: Set<String>): Boolean {
        if (unreadable.isEmpty()) return true
        if (isHidden(songId, unreadable)) return false
        if (readFromFolder && treeOf(songId) != null && folderOf(songId, folders) == null) return false
        return true
    }

    /** Which of the chosen [folders] [songId] came from, if any. */
    fun folderOf(songId: String, folders: Collection<String>): String? =
        folders.firstOrNull { isFrom(songId, it) }

    /**
     * Whether a song that would not play failed because its folder is gone: it was read from a
     * chosen folder, and that folder ([songTreeReadable] says) cannot be read now. A song that is
     * not from a chosen folder, or whose folder reads fine, failed for some other reason.
     */
    fun folderGone(songId: String?, songTreeReadable: Boolean?): Boolean =
        songId != null && treeOf(songId) != null && songTreeReadable == false

    /** The folders after [old] is replaced by [new]; the same set when they are one folder. */
    fun replace(folders: Set<String>, old: String, new: String): Set<String> =
        if (old == new) folders + new else (folders - old) + new

    /**
     * Where a song's file sits, as compared across folders. On Android's own storage a file id is
     * "<volume>:<path>" ("1A2B-3C4D:Music/Mara Quill/Lantern.mp3"): the path after the volume is
     * the same whichever card it is on and whichever folder on it was chosen. For another
     * provider, the path below the chosen folder. Null when the address says neither.
     */
    fun placeOf(songId: String): String? {
        if (treeOf(songId) == null) return null
        val treeId = decode(songId.substringAfter(TREE).substringBefore(DOCUMENT)) ?: return null
        val fileId = decode(songId.substringAfter(DOCUMENT)) ?: return null
        val volume = fileId.substringBefore(':', "")
        if (volume.isNotEmpty() && '/' !in volume) {
            return fileId.substringAfter(':').trim('/').takeIf { it.isNotEmpty() }
        }
        // Files (Cabinet) names a file by its path: "/storage/1A2B-3C4D/Music/..." on a card,
        // "/storage/emulated/0/Music/..." on the phone. The same place: what follows the volume.
        STORAGE_PATH.matchEntire(fileId)?.let { return it.groupValues[1].trim('/').takeIf { p -> p.isNotEmpty() } }
        val prefix = treeId.trimEnd('/') + "/"
        return if (fileId.startsWith(prefix)) fileId.removePrefix(prefix).takeIf { it.isNotEmpty() } else null
    }

    /**
     * Which leaving song is found again as which arriving one: [leaving] are songs about to go
     * from the library (their folder let go), [arriving] songs new to it this scan. A pair is
     * made only where the place matches exactly one on each side, so two copies of one file never
     * share out one song's hearts by guesswork. Returns leaving id to arriving id.
     */
    fun carryOver(leaving: Collection<String>, arriving: Collection<String>): Map<String, String> {
        if (leaving.isEmpty() || arriving.isEmpty()) return emptyMap()
        val from = leaving.groupBy { placeOf(it) }.filterKeys { it != null }
        val to = arriving.groupBy { placeOf(it) }.filterKeys { it != null }
        val result = mutableMapOf<String, String>()
        for ((place, olds) in from) {
            val news = to[place] ?: continue
            if (olds.size == 1 && news.size == 1 && olds[0] != news[0]) result[olds[0]] = news[0]
        }
        return result
    }

    // Android writes these addresses with Uri.encode, which never leaves a bare '+', so the
    // form decoder reads them back exactly.
    private fun decode(part: String): String? = runCatching { URLDecoder.decode(part, "UTF-8") }.getOrNull()
}

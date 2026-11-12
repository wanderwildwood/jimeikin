package com.wanderwildwood.jimeikin.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.id3.ID3v23Tag
import java.io.File
import java.io.FileOutputStream

/**
 * Writes a song's tags back into the file it came from.
 *
 * The file is someone's music and the phone can lose power or have its card pulled at any
 * moment, so nothing here writes over it in place. The tags are written into a copy in the
 * cache, the copy is read back to prove the write took, and the copy is then laid down beside
 * the original under a name no scan indexes ([PENDING_SUFFIX]). Only once it is whole on the
 * card is the original deleted and the copy renamed into its place. Whatever moment this is
 * interrupted at, a whole file is left: the untouched original, or the finished copy under
 * the pending name, which [recoverPending] finishes renaming on the next scan.
 */
object TagEditor {
    const val PENDING_SUFFIX = ".jimeikin-new"

    /** Set while an edit is on the card, so a scan does not take its copy for an orphan. */
    @Volatile
    var isWriting = false
        private set

    /** The fields a reader can change. In [write], a null field is left as the file has it. */
    data class Fields(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val albumArtist: String? = null,
        val trackNumber: String? = null,
    )

    sealed class Result {
        /** [uri] is where the file is now; the same as before on the phone's own storage. */
        data class Written(val uri: Uri) : Result()

        /** The format could not be read or written - the original was never touched. */
        object Unsupported : Result()

        /** Something failed before the original was touched: [step] says what. */
        data class Failed(val step: Step, val cause: Throwable? = null) : Result()

        /** The original is gone and the edited copy could not take its name. */
        object LeftPending : Result()
    }

    /** Where a save stopped. Each is before the original file is touched. */
    enum class Step {
        /** The file would not give its name: it has gone, or the folder is no longer granted. */
        FIND_FILE,

        /** The file could not be copied into the app's own space to work on. */
        COPY_OUT,

        /** The edited copy could not be read back. */
        READ_BACK,

        /** The edited copy read back with something other than what was typed. */
        FIELDS_DIFFER,

        /** The folder the file is in could not be found from the folder that was chosen. */
        FIND_FOLDER,

        /** The folder would not take a new file. */
        CREATE_COPY,

        /** The edited copy could not be written out whole beside the original. */
        WRITE_COPY,

        /** The original would not make way for the edited copy. */
        REMOVE_ORIGINAL,
    }

    fun read(context: Context, uri: Uri): Fields? {
        val name = displayName(context, uri) ?: return null
        val temp = copyToCache(context, uri, name) ?: return null
        return try {
            TagOptionSingleton.getInstance().isAndroid = true
            val tag = AudioFileIO.read(temp).tag ?: return Fields()
            Fields(
                title = tag.getFirst(FieldKey.TITLE),
                artist = tag.getFirst(FieldKey.ARTIST),
                album = tag.getFirst(FieldKey.ALBUM),
                albumArtist = tag.getFirst(FieldKey.ALBUM_ARTIST),
                trackNumber = tag.getFirst(FieldKey.TRACK).substringBefore('/'),
            )
        } catch (_: Exception) {
            null
        } finally {
            temp.delete()
        }
    }

    fun write(context: Context, uri: Uri, fields: Fields): Result {
        isWriting = true
        try {
            return writeCopy(context, uri, fields)
        } finally {
            isWriting = false
        }
    }

    private fun writeCopy(context: Context, uri: Uri, fields: Fields): Result {
        val name = displayName(context, uri) ?: return Result.Failed(Step.FIND_FILE)
        val temp = copyToCache(context, uri, name) ?: return Result.Failed(Step.COPY_OUT)
        try {
            editFile(temp, fields)?.let { return it }
            return replace(context, uri, name, temp)
        } finally {
            temp.delete()
        }
    }

    /**
     * Writes [fields] into [file], a working copy, and reads it back to prove the write took.
     * Returns null when it did, or why it did not.
     */
    internal fun editFile(file: File, fields: Fields): Result? {
        TagOptionSingleton.getInstance().isAndroid = true
        // A YouTube download is WebM saved under .m4a and fails here, before anything on
        // the card has been touched.
        val audioFile = try {
            AudioFileIO.read(file)
        } catch (_: Exception) {
            return Result.Unsupported
        }
        try {
            // An MP3 with only the old tag at its end (ID3v1) is given the newer kind to hold
            // the edit, which every reader then prefers. It starts from what the old one
            // says: started empty, a song given a track number lost its title, artist and
            // album from every list.
            if (audioFile is MP3File && !audioFile.hasID3v2Tag() && audioFile.hasID3v1Tag()) {
                audioFile.tag = ID3v23Tag(audioFile.iD3v1Tag)
            }
            val tag = audioFile.tagOrCreateAndSetDefault
            tag.put(FieldKey.TITLE, fields.title)
            tag.put(FieldKey.ARTIST, fields.artist)
            tag.put(FieldKey.ALBUM, fields.album)
            tag.put(FieldKey.ALBUM_ARTIST, fields.albumArtist)
            tag.put(FieldKey.TRACK, fields.trackNumber)
            audioFile.commit()
        } catch (_: Exception) {
            return Result.Unsupported
        }

        val check = try {
            AudioFileIO.read(file).tag
        } catch (e: Exception) {
            return Result.Failed(Step.READ_BACK, e)
        } ?: return Result.Failed(Step.READ_BACK)
        if (!check.agrees(FieldKey.TITLE, fields.title) ||
            !check.agrees(FieldKey.ARTIST, fields.artist) ||
            !check.agrees(FieldKey.ALBUM, fields.album) ||
            !check.agrees(FieldKey.ALBUM_ARTIST, fields.albumArtist) ||
            !check.agrees(FieldKey.TRACK, fields.trackNumber)
        ) {
            return Result.Failed(Step.FIELDS_DIFFER)
        }
        return null
    }

    /**
     * Finishes an edit that was interrupted. With the original still there, the copy was not
     * yet known to be whole and is thrown away; with the original gone, the copy was, and it
     * takes the original's name. Returns the document id the file ends up under, or null if
     * there is no file to index.
     */
    fun recoverPending(
        context: Context,
        treeUri: Uri,
        pendingDocumentId: String,
        pendingName: String,
        siblingNames: Set<String>,
    ): String? {
        val resolver = context.contentResolver
        val pendingUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, pendingDocumentId)
        val originalName = pendingName.removeSuffix(PENDING_SUFFIX)
        return try {
            if (originalName in siblingNames) {
                DocumentsContract.deleteDocument(resolver, pendingUri)
                null
            } else {
                DocumentsContract.renameDocument(resolver, pendingUri, originalName)
                    ?.let { DocumentsContract.getDocumentId(it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun replace(context: Context, uri: Uri, name: String, edited: File): Result {
        val resolver = context.contentResolver
        // What is being attempted, for when the folder's provider throws instead of answering.
        var step = Step.FIND_FOLDER
        return try {
            val parentId = parentIdOf(context, uri) ?: return Result.Failed(Step.FIND_FOLDER)
            val pendingName = name + PENDING_SUFFIX
            step = Step.CREATE_COPY

            // A copy left by an edit that failed before it was whole.
            childByName(context, uri, parentId, pendingName)?.let {
                DocumentsContract.deleteDocument(resolver, it)
            }

            val parentUri = DocumentsContract.buildDocumentUriUsingTree(uri, parentId)
            val pendingUri = DocumentsContract.createDocument(
                resolver,
                parentUri,
                "application/octet-stream",
                pendingName,
            ) ?: return Result.Failed(Step.CREATE_COPY)

            var copyError: Throwable? = null
            val copied = try {
                resolver.openOutputStream(pendingUri, "w")?.use { output ->
                    edited.inputStream().use { it.copyTo(output) }
                    output.flush()
                    (output as? FileOutputStream)?.fd?.sync()
                } != null
            } catch (e: Exception) {
                copyError = e
                false
            }
            val pendingSize = sizeOf(context, pendingUri)
            val pendingIs = displayName(context, pendingUri)
            if (!copied || pendingSize != edited.length() || pendingIs != pendingName) {
                runCatching { DocumentsContract.deleteDocument(resolver, pendingUri) }
                return Result.Failed(
                    Step.WRITE_COPY,
                    copyError ?: IllegalStateException(
                        "size $pendingSize of ${edited.length()}, name kept: ${pendingIs == pendingName}",
                    ),
                )
            }

            step = Step.REMOVE_ORIGINAL
            if (!DocumentsContract.deleteDocument(resolver, uri)) {
                runCatching { DocumentsContract.deleteDocument(resolver, pendingUri) }
                return Result.Failed(Step.REMOVE_ORIGINAL)
            }
            val renamed = try {
                DocumentsContract.renameDocument(resolver, pendingUri, name)
            } catch (_: Exception) {
                null
            } ?: return Result.LeftPending
            Result.Written(renamed)
        } catch (e: Exception) {
            Result.Failed(step, e)
        }
    }

    /**
     * The id of the folder [uri]'s file is in. The folder's provider is asked first. Answering
     * is optional, though, and a folder chosen through another file manager may come from a
     * provider that does not: every save from such a folder used to stop here. Then the folder
     * is found by looking, see [findParent].
     */
    private fun parentIdOf(context: Context, uri: Uri): String? {
        val asked = try {
            DocumentsContract.findDocumentPath(context.contentResolver, uri)?.path
        } catch (_: Exception) {
            null
        }
        if (asked != null && asked.size >= 2) return asked[asked.size - 2]
        return findParent(DocumentsContract.getTreeDocumentId(uri), DocumentsContract.getDocumentId(uri)) { folderId ->
            childrenOf(context, uri, folderId)
        }
    }

    /**
     * The folder under [treeId] that lists [documentId] among its files. Most providers name a
     * document by its path, so the id up to its last slash is tried first; if that folder does
     * not list it, the tree is walked. [children] gives a folder's entries as id to
     * is-a-folder, or null for a folder that will not answer.
     */
    internal fun findParent(
        treeId: String,
        documentId: String,
        children: (folderId: String) -> List<Pair<String, Boolean>>?,
    ): String? {
        val byPath = documentId.substringBeforeLast('/', "")
        if (byPath.isNotEmpty() && children(byPath)?.any { it.first == documentId } == true) return byPath

        val seen = HashSet<String>()
        val stack = ArrayDeque(listOf(treeId))
        while (stack.isNotEmpty()) {
            val folderId = stack.removeLast()
            if (!seen.add(folderId)) continue
            for ((id, isFolder) in children(folderId) ?: continue) {
                if (id == documentId) return folderId
                if (isFolder) stack.addLast(id)
            }
        }
        return null
    }

    private fun childrenOf(context: Context, treeUri: Uri, folderId: String): List<Pair<String, Boolean>>? = try {
        context.contentResolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null,
            null,
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    add(id to (cursor.getString(1) == DocumentsContract.Document.MIME_TYPE_DIR))
                }
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun Tag.put(key: FieldKey, value: String?) {
        value ?: return
        val trimmed = value.trim()
        if (trimmed.isNotEmpty()) {
            setField(key, trimmed)
            return
        }
        deleteField(key)
        // "3/12" with only the 3 taken away reads back as track 0 of 12.
        if (key == FieldKey.TRACK) runCatching { deleteField(FieldKey.TRACK_TOTAL) }
    }

    private fun Tag.agrees(key: FieldKey, value: String?): Boolean {
        value ?: return true
        val read = getFirst(key).orEmpty()
        return if (key == FieldKey.TRACK) {
            read.substringBefore('/').trim().trimStart('0') == value.trim().trimStart('0')
        } else {
            read == value.trim()
        }
    }

    private fun childByName(context: Context, treeUri: Uri, parentId: String, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        context.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
                }
            }
        }
        return null
    }

    private fun displayName(context: Context, uri: Uri): String? = queryColumn(context, uri) {
        it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
    }

    private fun sizeOf(context: Context, uri: Uri): Long? = queryColumn(context, uri) {
        it.getLong(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE))
    }

    private fun <T> queryColumn(context: Context, uri: Uri, read: (android.database.Cursor) -> T): T? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) read(cursor) else null
        }
    } catch (_: Exception) {
        null
    }

    /** jaudiotagger picks its reader by extension, so the copy keeps the file's own. */
    private fun copyToCache(context: Context, uri: Uri, name: String): File? {
        val ext = name.substringAfterLast('.', "").lowercase().ifEmpty { return null }
        val temp = try {
            File.createTempFile("tag_edit", ".$ext", context.cacheDir)
        } catch (_: Exception) {
            return null
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { input.copyTo(it) }
            } ?: run {
                temp.delete()
                return null
            }
            temp
        } catch (_: Exception) {
            temp.delete()
            null
        }
    }
}

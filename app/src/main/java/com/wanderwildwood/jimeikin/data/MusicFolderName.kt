package com.wanderwildwood.jimeikin.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * What a chosen music folder is called on screen. A folder is kept as the address its picker
 * gave, and that address is never what is shown.
 *
 * A folder from Android's own picker reads as where it is: "Phone > Music > Albums". A folder
 * from another file manager is named by what that app calls it, and if it will not say, by
 * the last part of the folder's id, which is nearly always the folder's own name.
 */
object MusicFolderName {

    private const val ANDROIDS_OWN = "com.android.externalstorage.documents"

    /** The name without asking the folder's app anything: safe to call while drawing. */
    fun quick(uriString: String, phone: String, card: String): String = name(uriString, null, phone, card)

    /** The name, asking the folder's app what it calls the folder. Not for the main thread. */
    fun read(context: Context, uriString: String, phone: String, card: String): String =
        name(uriString, displayName(context, uriString), phone, card)

    private fun name(uriString: String, displayName: String?, phone: String, card: String): String {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull()
        val id = uri?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
            ?: uri?.lastPathSegment
            ?: ""
        return of(id, uri?.authority == ANDROIDS_OWN, displayName, phone, card)
    }

    private fun displayName(context: Context, uriString: String): String? {
        return try {
            val tree = Uri.parse(uriString)
            // Android's own folders are named by where they are; there is nothing to ask.
            if (tree.authority == ANDROIDS_OWN) return null
            val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(
                folder,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * The name for the folder whose id is [documentId]. [androidsOwn] is whether Android's own
     * picker gave it: its ids are "primary:Music/Albums" on the phone and "<card>:Music" on a
     * card, and read as "Phone > Music > Albums". Otherwise [displayName], what the folder's
     * app calls it, and failing that the last part of the id.
     */
    fun of(documentId: String, androidsOwn: Boolean, displayName: String?, phone: String, card: String): String {
        if (androidsOwn) volumePath(documentId, phone, card)?.let { return it }
        displayName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return lastPart(documentId)
    }

    private fun volumePath(documentId: String, phone: String, card: String): String? {
        val volume = documentId.substringBefore(':', "")
        if (volume.isEmpty() || '/' in volume) return null
        val path = documentId.substringAfter(':', "").trim('/').replace("/", " > ")
        val label = if (volume.equals("primary", ignoreCase = true)) phone else card
        return if (path.isEmpty()) label else "$label > $path"
    }

    /** "/storage/emulated/0/Music/Albums" and "primary:Music/Albums" are both "Albums". */
    private fun lastPart(documentId: String): String {
        val trimmed = documentId.trimEnd('/')
        return trimmed.substringAfterLast('/').substringAfterLast(':').ifEmpty { trimmed }
    }
}

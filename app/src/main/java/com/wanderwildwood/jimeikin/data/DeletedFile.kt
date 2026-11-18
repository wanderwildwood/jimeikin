package com.wanderwildwood.jimeikin.data

/**
 * Whether one of your own files is gone after asking for it to be deleted, so that its row and
 * its playlist places go only with the file.
 *
 * DocumentFile's delete() and exists() both answer false when the provider throws (a folder
 * whose permission was dropped, a card provider error), and "not there" used to be read from
 * that false: a file the app could not reach counted as deleted and lost its row while it
 * stayed on the phone. Asking for the document itself does not help either: a documents
 * provider gives no answer at all for a file that is not there (it throws FileNotFoundException
 * inside and query() answers null), the same as for one it could not reach. So "not there" is
 * read from the folder it was in: a folder that lists its files and leaves this one out.
 */
object DeletedFile {
    /**
     * [deleted]: the provider said the delete was done. Otherwise [documentId] is looked for in
     * the folder its id names (the id up to its last slash, as the phone's own storage and a
     * card name their documents). [listFolder] gives a folder's document ids, or null when the
     * folder gave no answer; it may throw. A folder that answers and does not list the file
     * means it is gone. A folder that does not answer may itself be gone (a whole album folder
     * removed in a file manager), so the folder above it is asked about it in turn, never
     * above [treeId], the folder you chose. No answer anywhere is not taken for "not there": a
     * card that is out of the phone answers like that, and the file is still on that card.
     */
    fun isGone(
        deleted: Boolean,
        treeId: String?,
        documentId: String?,
        listFolder: (folderId: String) -> List<String>?,
    ): Boolean {
        if (deleted) return true
        if (treeId == null || documentId == null) return false
        if (documentId != treeId && !documentId.startsWith("$treeId/")) return false
        var child: String = documentId
        while (child != treeId) {
            val folder = child.substringBeforeLast('/', "")
            if (folder.isEmpty() || (folder != treeId && !folder.startsWith("$treeId/"))) return false
            val listed = try {
                listFolder(folder)
            } catch (_: Exception) {
                null
            }
            if (listed != null) return child !in listed
            child = folder
        }
        return false
    }
}

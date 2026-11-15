package com.wanderwildwood.jimeikin.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which chosen music folders could not be read when last looked at: a memory card out or
 * swapped, a folder gone, or its permission taken back. Their songs are hidden from the library
 * until they can be read again; see [MusicFolderSongs].
 */
object FolderReach {

    private val _unreadable = MutableStateFlow<Set<String>>(emptySet())
    val unreadable: StateFlow<Set<String>> = _unreadable.asStateFlow()

    /** What the last look found, for the folders that are chosen now. */
    fun update(folders: Set<String>, unreadable: Set<String>) {
        _unreadable.value = unreadable intersect folders
    }

    /**
     * Whether the folder [treeUri] can be read now. Disk work: not for the main thread.
     *
     * DocumentFile swallows its own query failures and answers false, so a revoked grant, an
     * unmounted card and a folder that has gone away all look the same here.
     */
    fun isReadable(context: Context, treeUri: String): Boolean = try {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
        root != null && root.exists() && root.canRead()
    } catch (_: Exception) {
        false
    }
}

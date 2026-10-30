package com.wanderwildwood.jimeikin.data

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The names this app stored for online-catalogue songs before 2.18.14 ("YOUTUBE" and
 * "YOUTUBE_DOWNLOAD"), and how they become the neutral ones ("ONLINE_SEARCH" and
 * "ONLINE_SEARCH_DOWNLOAD") that the shared code now uses. They live only here, in the full app:
 * Music Box K is a new app with no old data, and its build holds none of these words (the store
 * source set has an empty twin of this file).
 */
object LegacyKinds {
    private const val OLD = "YOUTUBE"
    private const val OLD_DOWNLOAD = "YOUTUBE_DOWNLOAD"
    private const val NEW = "ONLINE_SEARCH"
    private const val NEW_DOWNLOAD = "ONLINE_SEARCH_DOWNLOAD"

    /** A kind as stored by any version, in today's name. Anything else is returned as it is. */
    fun current(kind: String): String = when (kind) {
        OLD -> NEW
        OLD_DOWNLOAD -> NEW_DOWNLOAD
        else -> kind
    }

    /** An id carrying a kind as its prefix ("YOUTUBE:artist"), in today's name. */
    fun currentId(id: String): String = when {
        id.startsWith("$OLD_DOWNLOAD:") -> NEW_DOWNLOAD + id.substring(OLD_DOWNLOAD.length)
        id.startsWith("$OLD:") -> NEW + id.substring(OLD.length)
        else -> id
    }

    /** The statements of the 11 to 12 migration, in order. Downloads first: "YOUTUBE_DOWNLOAD:" also starts with "YOUTUBE". */
    val statements: List<String> = buildList {
        for (table in listOf("songs", "albums", "artists", "hearted_songs")) {
            add("UPDATE $table SET sourceType = '$NEW_DOWNLOAD' WHERE sourceType = '$OLD_DOWNLOAD'")
            add("UPDATE $table SET sourceType = '$NEW' WHERE sourceType = '$OLD'")
        }
        // Ids that carry the kind as a prefix, and every column that names one.
        for ((table, column) in listOf(
            "artists" to "id", "albums" to "id", "albums" to "artistId",
            "songs" to "albumId", "songs" to "artistId", "name_overrides" to "targetId",
        )) {
            for ((old, new) in listOf(OLD_DOWNLOAD to NEW_DOWNLOAD, OLD to NEW)) {
                add("UPDATE $table SET $column = '$new:' || substr($column, ${old.length + 2}) WHERE $column LIKE '$old:%'")
            }
        }
    }

    fun migrate(db: SupportSQLiteDatabase) = statements.forEach(db::execSQL)
}

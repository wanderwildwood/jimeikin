package com.wanderwildwood.jimeikin.data

import androidx.sqlite.db.SupportSQLiteDatabase

/** Music Box K has no data from before its first version, so there is nothing to carry over. */
object LegacyKinds {
    fun current(kind: String): String = kind
    fun currentId(id: String): String = id
    fun migrate(db: SupportSQLiteDatabase) = Unit
}

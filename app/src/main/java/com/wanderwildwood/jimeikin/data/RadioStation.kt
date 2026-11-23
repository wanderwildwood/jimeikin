package com.wanderwildwood.jimeikin.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * A station kept for coming back to.
 *
 * It stores the stream url, not the radio.garden id, and that is the whole point: playing a
 * kept station asks nothing of radio.garden, so the list keeps working if their undocumented
 * api changes or goes away. The id is kept only so the same station is recognised as already
 * kept when it is met again in the lists.
 */
@Entity(tableName = "radio_stations")
data class RadioStationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val place: String,
    val country: String,
    val streamUrl: String,
    val keptAtMillis: Long,
    /** The folder it is kept in, or "" for none. A folder is only its name: it exists while a station is in it. */
    @ColumnInfo(defaultValue = "") val folder: String = "",
)

@Dao
interface RadioStationDao {

    @Query("SELECT * FROM radio_stations ORDER BY keptAtMillis DESC")
    suspend fun getAll(): List<RadioStationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun keep(station: RadioStationEntity)

    @Query("DELETE FROM radio_stations WHERE id = :id")
    suspend fun forget(id: String)

    @Query("SELECT id FROM radio_stations")
    suspend fun keptIds(): List<String>

    @Query("UPDATE radio_stations SET folder = :folder WHERE id = :id")
    suspend fun setFolder(id: String, folder: String)

    @Query("UPDATE radio_stations SET folder = :to WHERE folder = :from")
    suspend fun renameFolder(from: String, to: String)

    @Query("DELETE FROM radio_stations")
    suspend fun forgetAll()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun keepAll(stations: List<RadioStationEntity>)
}

package com.yourname.touchbase.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A saved, reusable combination of the contacts screen's tag/date/sort
 * controls - e.g. "VIPs added this month". dateFilter/sortOrder are stored
 * as the UI-layer enum's .name rather than typed here, so this data-layer
 * entity doesn't depend on the ui.contacts package.
 */
@Entity(tableName = "saved_filters")
data class SavedFilter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val tagId: Long? = null,
    val dateFilter: String,
    val sortOrder: String
)

@Dao
interface SavedFilterDao {
    @Query("SELECT * FROM saved_filters ORDER BY id ASC")
    fun observeAll(): Flow<List<SavedFilter>>

    @Insert
    suspend fun insert(filter: SavedFilter): Long

    @Delete
    suspend fun delete(filter: SavedFilter)
}

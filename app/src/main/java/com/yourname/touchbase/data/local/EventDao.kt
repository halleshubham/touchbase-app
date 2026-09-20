package com.yourname.touchbase.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Query("SELECT * FROM events ORDER BY eventEpochMillis ASC")
    fun observeAll(): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE contactId = :contactId ORDER BY eventEpochMillis ASC")
    fun observeForContact(contactId: Long): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE eventEpochMillis >= :fromMillis ORDER BY eventEpochMillis ASC")
    suspend fun getUpcoming(fromMillis: Long): List<Event>

    @Upsert
    suspend fun upsert(event: Event): Long

    @Delete
    suspend fun delete(event: Event)

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: Long): Event?
}

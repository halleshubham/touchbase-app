package com.yourname.touchbase.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** One row of the call queue: the session item plus the contact it points to. */
data class QueueRow(
    @Embedded val item: CallSessionItem,
    @Embedded(prefix = "contact_") val contact: ContactSlim
)

data class ContactSlim(
    val id: Long,
    val displayName: String,
    val phoneNumber: String
)

@Dao
interface CallSessionDao {

    @Insert
    suspend fun insertSession(session: CallSession): Long

    @Insert
    suspend fun insertItems(items: List<CallSessionItem>)

    @Update
    suspend fun updateSession(session: CallSession)

    @Update
    suspend fun updateItem(item: CallSessionItem)

    @Query("SELECT * FROM call_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: Long): CallSession?

    @Query(
        """
        SELECT
            call_session_items.*,
            contacts.id AS contact_id,
            contacts.displayName AS contact_displayName,
            contacts.phoneNumber AS contact_phoneNumber
        FROM call_session_items
        INNER JOIN contacts ON contacts.id = call_session_items.contactId
        WHERE call_session_items.sessionId = :sessionId
        ORDER BY call_session_items.orderIndex ASC
        """
    )
    fun observeQueue(sessionId: Long): Flow<List<QueueRow>>

    @Query("SELECT * FROM call_sessions ORDER BY createdAt DESC LIMIT 20")
    fun observeRecentSessions(): Flow<List<CallSession>>

    @Query(
        """
        SELECT
            call_session_items.*,
            contacts.id AS contact_id,
            contacts.displayName AS contact_displayName,
            contacts.phoneNumber AS contact_phoneNumber
        FROM call_session_items
        INNER JOIN contacts ON contacts.id = call_session_items.contactId
        WHERE call_session_items.contactId = :contactId
        ORDER BY call_session_items.calledAtEpochMillis DESC
        """
    )
    fun observeHistoryForContact(contactId: Long): Flow<List<QueueRow>>
}

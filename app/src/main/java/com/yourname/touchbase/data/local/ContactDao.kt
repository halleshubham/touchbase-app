package com.yourname.touchbase.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    // Single flexible query for the paged list - see dev-log/DEVELOPMENT_LOG.md (2026-09-20).
    @Transaction
    @Query(
        """
        SELECT DISTINCT contacts.* FROM contacts
        LEFT JOIN contact_tag_cross_ref ON contacts.id = contact_tag_cross_ref.contactId
        WHERE (:tagId IS NULL OR contact_tag_cross_ref.tagId = :tagId)
          AND (:minTimestampAdded IS NULL OR contacts.rawTimestampAdded >= :minTimestampAdded)
        ORDER BY
            CASE WHEN :sortAscending = 0 THEN contacts.rawTimestampAdded END DESC,
            CASE WHEN :sortAscending = 1 THEN contacts.rawTimestampAdded END ASC
        """
    )
    fun pagedContacts(
        tagId: Long?,
        minTimestampAdded: Long?,
        sortAscending: Boolean
    ): PagingSource<Int, ContactWithTags>

    // Full (non-paged) list, for callers that need everything at once - see dev-log/DEVELOPMENT_LOG.md (2026-09-20).
    @Transaction
    @Query("SELECT * FROM contacts ORDER BY rawTimestampAdded DESC")
    fun observeAllWithTags(): Flow<List<ContactWithTags>>

    @Upsert
    suspend fun upsert(contact: Contact): Long

    @Upsert
    suspend fun upsertAll(contacts: List<Contact>)

    @Delete
    suspend fun delete(contact: Contact)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: Tag): Long

    @Query("SELECT * FROM tags ORDER BY label ASC")
    fun observeAllTags(): Flow<List<Tag>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToContact(crossRef: ContactTagCrossRef)

    @Query("DELETE FROM contact_tag_cross_ref WHERE contactId = :contactId AND tagId = :tagId")
    suspend fun removeTagFromContact(contactId: Long, tagId: Long)

    @Query("SELECT * FROM contacts WHERE systemContactId = :systemId LIMIT 1")
    suspend fun findBySystemId(systemId: Long): Contact?

    @Query("SELECT * FROM contacts WHERE systemContactId IN (:systemIds)")
    suspend fun findBySystemIds(systemIds: List<Long>): List<Contact>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): Contact?

    @Query("SELECT * FROM contacts WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSync(): List<Contact>
}

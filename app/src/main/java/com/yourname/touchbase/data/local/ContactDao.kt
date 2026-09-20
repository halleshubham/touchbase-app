package com.yourname.touchbase.data.local

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

    @Transaction
    @Query("SELECT * FROM contacts ORDER BY rawTimestampAdded DESC")
    fun observeAllWithTags(): Flow<List<ContactWithTags>>

    @Transaction
    @Query(
        """
        SELECT DISTINCT contacts.* FROM contacts
        INNER JOIN contact_tag_cross_ref ON contacts.id = contact_tag_cross_ref.contactId
        WHERE contact_tag_cross_ref.tagId = :tagId
        ORDER BY rawTimestampAdded DESC
        """
    )
    fun observeByTag(tagId: Long): Flow<List<ContactWithTags>>

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

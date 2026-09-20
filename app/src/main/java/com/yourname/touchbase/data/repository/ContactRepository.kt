package com.yourname.touchbase.data.repository

import androidx.paging.PagingSource
import com.yourname.touchbase.data.local.Contact
import com.yourname.touchbase.data.local.ContactDao
import com.yourname.touchbase.data.local.ContactTagCrossRef
import com.yourname.touchbase.data.local.ContactWithTags
import com.yourname.touchbase.data.local.SavedFilter
import com.yourname.touchbase.data.local.SavedFilterDao
import com.yourname.touchbase.data.local.SyncStatus
import com.yourname.touchbase.data.local.Tag
import kotlinx.coroutines.flow.Flow
import android.accounts.Account
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val dao: ContactDao,
    private val systemSource: SystemContactsSource,
    private val savedFilterDao: SavedFilterDao
) {

    fun pagedContacts(
        tagId: Long?,
        minTimestampAdded: Long?,
        sortAscending: Boolean
    ): PagingSource<Int, ContactWithTags> = dao.pagedContacts(tagId, minTimestampAdded, sortAscending)

    fun observeTags(): Flow<List<Tag>> = dao.observeAllTags()

    /** Full (non-paged) list - see ContactDao.observeAllWithTags(). */
    fun observeContacts(): Flow<List<ContactWithTags>> = dao.observeAllWithTags()

    /**
     * Pulls every phone-having contact from ContactsContract and upserts a
     * shadow row for each into Room, matched by systemContactId. Existing
     * tag assignments are untouched since we only ever touch the Contact row.
     * Call this on first launch and on pull-to-refresh.
     */
    suspend fun refreshFromSystemContacts() {
        val systemContacts = systemSource.readAllContacts()
        val existingBySystemId = dao.findBySystemIds(systemContacts.map { it.systemContactId })
            .associateBy { it.systemContactId }
        // Only rows that are new or actually changed - see dev-log/DEVELOPMENT_LOG.md (2026-09-20).
        val changedRows = systemContacts.mapNotNull { sys ->
            val existing = existingBySystemId[sys.systemContactId]
            if (existing != null && existing.displayName == sys.displayName && existing.phoneNumber == sys.phoneNumber) {
                null
            } else {
                Contact(
                    id = existing?.id ?: 0,
                    systemContactId = sys.systemContactId,
                    displayName = sys.displayName,
                    phoneNumber = sys.phoneNumber,
                    rawTimestampAdded = existing?.rawTimestampAdded ?: sys.lastUpdatedTimestamp,
                    syncStatus = existing?.syncStatus ?: SyncStatus.SYNCED
                )
            }
        }
        if (changedRows.isNotEmpty()) dao.upsertAll(changedRows)
    }

    /**
     * Quick-add flow: writes to the real address book (under [account] if
     * given, so Android's built-in sync adapter pushes it to Google; local-
     * only otherwise), then mirrors it into Room.
     */
    suspend fun quickAdd(displayName: String, phoneNumber: String, account: Account? = null) {
        val systemId = systemSource.quickAddContact(displayName, phoneNumber, account)
        dao.upsert(
            Contact(
                systemContactId = if (systemId > 0) systemId else null,
                displayName = displayName,
                phoneNumber = phoneNumber,
                rawTimestampAdded = System.currentTimeMillis(),
                syncStatus = if (systemId > 0) SyncStatus.SYNCED else SyncStatus.PENDING
            )
        )
    }

    suspend fun createTag(label: String, colorHex: String = "#6750A4"): Long =
        dao.insertTag(Tag(label = label, colorHex = colorHex))

    suspend fun assignTag(contactId: Long, tagId: Long) =
        dao.addTagToContact(ContactTagCrossRef(contactId, tagId))

    // "Create a list from selected contacts" is bulk tag assignment - see dev-log/DEVELOPMENT_LOG.md (2026-09-20).
    suspend fun createTagWithContacts(label: String, contactIds: List<Long>): Long {
        val tagId = createTag(label)
        dao.addTagToContacts(contactIds.map { ContactTagCrossRef(it, tagId) })
        return tagId
    }

    suspend fun removeTag(contactId: Long, tagId: Long) =
        dao.removeTagFromContact(contactId, tagId)

    suspend fun renameTag(tagId: Long, label: String) = dao.renameTag(tagId, label)

    // Cross-refs have no FK cascade declared, so clear them explicitly before dropping the tag row -
    // see dev-log/DEVELOPMENT_LOG.md (2026-09-20).
    suspend fun deleteTag(tagId: Long) {
        dao.removeAllContactsFromTag(tagId)
        dao.deleteTagById(tagId)
    }

    suspend fun getContactIdsForTag(tagId: Long): List<Long> = dao.getContactIdsForTag(tagId)

    suspend fun updateTagMembers(tagId: Long, newContactIds: Set<Long>) {
        val current = dao.getContactIdsForTag(tagId).toSet()
        val toAdd = newContactIds - current
        val toRemove = current - newContactIds
        if (toAdd.isNotEmpty()) dao.addTagToContacts(toAdd.map { ContactTagCrossRef(it, tagId) })
        toRemove.forEach { dao.removeTagFromContact(it, tagId) }
    }

    fun observeSavedFilters(): Flow<List<SavedFilter>> = savedFilterDao.observeAll()

    suspend fun saveFilter(name: String, tagId: Long?, dateFilterName: String, sortOrderName: String): Long =
        savedFilterDao.insert(
            SavedFilter(name = name, tagId = tagId, dateFilter = dateFilterName, sortOrder = sortOrderName)
        )

    suspend fun renameSavedFilter(filter: SavedFilter, newName: String) =
        savedFilterDao.update(filter.copy(name = newName))

    suspend fun deleteFilter(filter: SavedFilter) = savedFilterDao.delete(filter)
}

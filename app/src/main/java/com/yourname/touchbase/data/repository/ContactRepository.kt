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
        val shadowRows = systemContacts.map { sys ->
            val existing = existingBySystemId[sys.systemContactId]
            Contact(
                id = existing?.id ?: 0,
                systemContactId = sys.systemContactId,
                displayName = sys.displayName,
                phoneNumber = sys.phoneNumber,
                // Stamped ONCE, the first time this contact is seen, then
                // never touched again. sys.lastUpdatedTimestamp is not a
                // creation date (see SystemContact) - re-reading it on every
                // refresh (the previous behavior) made long-existing
                // contacts intermittently look freshly added whenever the
                // OS bumped their metadata for an unrelated reason.
                rawTimestampAdded = existing?.rawTimestampAdded ?: sys.lastUpdatedTimestamp,
                syncStatus = existing?.syncStatus ?: SyncStatus.SYNCED
            )
        }
        dao.upsertAll(shadowRows)
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

    suspend fun removeTag(contactId: Long, tagId: Long) =
        dao.removeTagFromContact(contactId, tagId)

    fun observeSavedFilters(): Flow<List<SavedFilter>> = savedFilterDao.observeAll()

    suspend fun saveFilter(name: String, tagId: Long?, dateFilterName: String, sortOrderName: String): Long =
        savedFilterDao.insert(
            SavedFilter(name = name, tagId = tagId, dateFilter = dateFilterName, sortOrder = sortOrderName)
        )

    suspend fun deleteFilter(filter: SavedFilter) = savedFilterDao.delete(filter)
}

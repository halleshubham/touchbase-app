package com.yourname.touchbase.data.repository

import com.yourname.touchbase.data.local.Contact
import com.yourname.touchbase.data.local.ContactDao
import com.yourname.touchbase.data.local.ContactTagCrossRef
import com.yourname.touchbase.data.local.ContactWithTags
import com.yourname.touchbase.data.local.SyncStatus
import com.yourname.touchbase.data.local.Tag
import kotlinx.coroutines.flow.Flow
import android.accounts.Account
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val dao: ContactDao,
    private val systemSource: SystemContactsSource
) {

    fun observeContacts(): Flow<List<ContactWithTags>> = dao.observeAllWithTags()

    fun observeTags(): Flow<List<Tag>> = dao.observeAllTags()

    /**
     * Pulls every phone-having contact from ContactsContract and upserts a
     * shadow row for each into Room, matched by systemContactId. Existing
     * tag assignments are untouched since we only ever touch the Contact row.
     * Call this on first launch and on pull-to-refresh.
     */
    suspend fun refreshFromSystemContacts() {
        val systemContacts = systemSource.readAllContacts()
        val shadowRows = systemContacts.map { sys ->
            val existing = dao.findBySystemId(sys.systemContactId)
            Contact(
                id = existing?.id ?: 0,
                systemContactId = sys.systemContactId,
                displayName = sys.displayName,
                phoneNumber = sys.phoneNumber,
                rawTimestampAdded = sys.timesContacted,
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
}

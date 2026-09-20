package com.yourname.touchbase.data.repository

import android.accounts.Account
import android.content.ContentUris
import android.content.Context
import android.provider.ContactsContract
import androidx.core.content.contentValuesOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SystemContact(
    val systemContactId: Long,
    val displayName: String,
    val phoneNumber: String,
    // NOT a "date added" - ContactsContract doesn't expose true creation
    // time. This moves whenever the OS touches the contact for any reason
    // (a call, a sync, photo/label changes, contact linking), so the
    // repository only uses it once, the first time a contact is seen -
    // see ContactRepository.refreshFromSystemContacts().
    val lastUpdatedTimestamp: Long
)

/**
 * Thin wrapper around ContactsContract. ContactsContract stays the source of
 * truth for name/number — we never copy it wholesale into Room, only read it
 * here and let the repository merge it with our local tag/metadata table.
 */
@Singleton
class SystemContactsSource @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Reads every contact with a phone number, newest-updated first. */
    suspend fun readAllContacts(): List<SystemContact> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SystemContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.CONTACT_LAST_UPDATED_TIMESTAMP
        )

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_LAST_UPDATED_TIMESTAMP} DESC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val updatedIdx = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.CONTACT_LAST_UPDATED_TIMESTAMP
            )

            val seenContactIds = mutableSetOf<Long>()
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(idIdx)
                // A contact can have multiple phone numbers; keep the first one for Phase 1.
                if (!seenContactIds.add(contactId)) continue

                results += SystemContact(
                    systemContactId = contactId,
                    displayName = cursor.getString(nameIdx) ?: "(No name)",
                    phoneNumber = cursor.getString(numberIdx) ?: "",
                    lastUpdatedTimestamp = cursor.getLong(updatedIdx)
                )
            }
        }
        results
    }

    /**
     * Quick-add: writes a minimal raw contact directly into ContactsContract
     * so it shows up in the user's real address book immediately, not just
     * inside this app. When [account] is a Google account already signed in
     * on this device, Android's own sync adapter picks up the new raw
     * contact and pushes it to Google automatically — no API calls of our
     * own required. When [account] is null, the contact is written without
     * an owning account (phone-local only, never leaves the device).
     * Returns the new system contact id.
     */
    suspend fun quickAddContact(
        displayName: String,
        phoneNumber: String,
        account: Account? = null
    ): Long =
        withContext(Dispatchers.IO) {
            val rawContactValues = contentValuesOf(
                ContactsContract.RawContacts.ACCOUNT_TYPE to account?.type,
                ContactsContract.RawContacts.ACCOUNT_NAME to account?.name
            )
            val rawContactUri = context.contentResolver.insert(
                ContactsContract.RawContacts.CONTENT_URI,
                rawContactValues
            ) ?: error("Failed to create raw contact")
            val rawContactId = ContentUris.parseId(rawContactUri)

            context.contentResolver.insert(
                ContactsContract.Data.CONTENT_URI,
                contentValuesOf(
                    ContactsContract.Data.RAW_CONTACT_ID to rawContactId,
                    ContactsContract.Data.MIMETYPE to ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME to displayName
                )
            )

            context.contentResolver.insert(
                ContactsContract.Data.CONTENT_URI,
                contentValuesOf(
                    ContactsContract.Data.RAW_CONTACT_ID to rawContactId,
                    ContactsContract.Data.MIMETYPE to ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Phone.NUMBER to phoneNumber,
                    ContactsContract.CommonDataKinds.Phone.TYPE to ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                )
            )

            // Look up the aggregated CONTACT_ID for this raw contact.
            var contactId = -1L
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.CONTACT_ID),
                "${ContactsContract.RawContacts._ID} = ?",
                arrayOf(rawContactId.toString()),
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    contactId = c.getLong(c.getColumnIndexOrThrow(ContactsContract.RawContacts.CONTACT_ID))
                }
            }
            contactId
        }
}

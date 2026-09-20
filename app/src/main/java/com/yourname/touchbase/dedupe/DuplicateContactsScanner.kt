package com.yourname.touchbase.dedupe

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import androidx.core.content.contentValuesOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ContactCard(
    val contactId: Long,
    val displayName: String,
    val rawContactIds: List<Long>,
    // The account-linked raw contact when one exists, else rawContactIds.first() -
    // new data must land here or it silently never reaches Google.
    val preferredRawContactId: Long,
    val phoneNumbers: List<String>,
    val emails: List<String>,
    val hasGoogleAccount: Boolean
)

data class DuplicateGroup(val cards: List<ContactCard>)

data class MergeOutcome(val keptContactId: Long, val removedContactIds: List<Long>)

/**
 * Detects contacts that Android's own aggregation engine did NOT already
 * combine (usually because display names differ) but that share a phone
 * number - see dev-log/DEVELOPMENT_LOG.md (2026-09-20) for the full design
 * discussion and known limitations (only phone/email are preserved from a
 * removed contact; other fields like notes/photos are not).
 */
@Singleton
class DuplicateContactsScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun normalizePhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    suspend fun scan(): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val cardsById = LinkedHashMap<Long, MutableCard>()

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )?.use { cursor ->
            val contactIdIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val rawIdIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(contactIdIdx)
                val card = cardsById.getOrPut(contactId) { MutableCard(contactId, cursor.getString(nameIdx) ?: "(No name)") }
                card.rawContactIds += cursor.getLong(rawIdIdx)
                cursor.getString(numberIdx)?.let { card.phoneNumbers += it }
            }
        }

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.CONTACT_ID, ContactsContract.CommonDataKinds.Email.ADDRESS),
            null, null, null
        )?.use { cursor ->
            val contactIdIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
            val addressIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(contactIdIdx)
                cardsById[contactId]?.let { card ->
                    cursor.getString(addressIdx)?.let { card.emails += it }
                }
            }
        }

        val rawIdsWithAccount = mutableSetOf<Long>()
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID, ContactsContract.RawContacts.ACCOUNT_TYPE),
            null, null, null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts._ID)
            val typeIdx = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_TYPE)
            while (cursor.moveToNext()) {
                if (cursor.getString(typeIdx) != null) rawIdsWithAccount += cursor.getLong(idIdx)
            }
        }

        val cards = cardsById.values.map { c ->
            val distinctRawIds = c.rawContactIds.distinct()
            ContactCard(
                contactId = c.contactId,
                displayName = c.displayName,
                rawContactIds = distinctRawIds,
                preferredRawContactId = distinctRawIds.firstOrNull { it in rawIdsWithAccount } ?: distinctRawIds.first(),
                phoneNumbers = c.phoneNumbers.distinct(),
                emails = c.emails.distinct(),
                hasGoogleAccount = distinctRawIds.any { it in rawIdsWithAccount }
            )
        }

        // Union contacts that share a normalized phone number into one cluster.
        val parent = cards.associate { it.contactId to it.contactId }.toMutableMap()
        fun find(x: Long): Long {
            var r = x
            while (parent.getValue(r) != r) r = parent.getValue(r)
            return r
        }
        fun union(a: Long, b: Long) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }
        val byNormalizedPhone = HashMap<String, MutableList<Long>>()
        cards.forEach { card ->
            card.phoneNumbers.map { normalizePhone(it) }.filter { it.length >= 7 }.distinct().forEach { key ->
                byNormalizedPhone.getOrPut(key) { mutableListOf() } += card.contactId
            }
        }
        byNormalizedPhone.values.forEach { ids -> ids.drop(1).forEach { union(ids.first(), it) } }

        cards.groupBy { find(it.contactId) }
            .values
            .filter { it.size > 1 }
            .map { DuplicateGroup(it.sortedByDescending { c -> c.hasGoogleAccount }) }
    }

    private fun choosePrimary(cards: List<ContactCard>): ContactCard =
        cards.maxWithOrNull(
            compareBy(
                { if (it.hasGoogleAccount) 1 else 0 },
                { it.phoneNumbers.size + it.emails.size }
            )
        ) ?: cards.first()

    suspend fun mergeGroup(group: DuplicateGroup): MergeOutcome = withContext(Dispatchers.IO) {
        val primary = choosePrimary(group.cards)
        val losers = group.cards.filter { it.contactId != primary.contactId }

        val primaryRawContactId = primary.preferredRawContactId
        val existingPhones = primary.phoneNumbers.map { normalizePhone(it) }.toMutableSet()
        val existingEmails = primary.emails.map { it.lowercase(Locale.ROOT) }.toMutableSet()

        val ops = ArrayList<ContentProviderOperation>()
        losers.forEach { loser ->
            loser.phoneNumbers.forEach { phone ->
                if (normalizePhone(phone) !in existingPhones) {
                    ops += insertDataOp(
                        primaryRawContactId,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        phone,
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                    )
                    existingPhones += normalizePhone(phone)
                }
            }
            loser.emails.forEach { email ->
                if (email.lowercase(Locale.ROOT) !in existingEmails) {
                    ops += insertDataOp(
                        primaryRawContactId,
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                        ContactsContract.CommonDataKinds.Email.ADDRESS,
                        email,
                        ContactsContract.CommonDataKinds.Email.TYPE,
                        ContactsContract.CommonDataKinds.Email.TYPE_HOME
                    )
                    existingEmails += email.lowercase(Locale.ROOT)
                }
            }
            loser.rawContactIds.forEach { rawId ->
                ops += ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                    .withSelection("${ContactsContract.RawContacts._ID} = ?", arrayOf(rawId.toString()))
                    .build()
            }
        }

        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        MergeOutcome(primary.contactId, losers.map { it.contactId })
    }

    private fun insertDataOp(rawContactId: Long, mimeType: String, column: String, value: String, typeColumn: String, typeValue: Int) =
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValues(
                contentValuesOf(
                    ContactsContract.Data.RAW_CONTACT_ID to rawContactId,
                    ContactsContract.Data.MIMETYPE to mimeType,
                    column to value,
                    typeColumn to typeValue
                )
            )
            .build()

    private fun escapeVCardText(value: String): String =
        value.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")

    // One re-importable vCard 3.0 file for the whole batch - see dev-log/DEVELOPMENT_LOG.md (2026-09-20).
    suspend fun writeBackup(cards: List<ContactCard>): String = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "touchbase_dedupe_backup_$stamp.vcf")
        val body = cards.joinToString("") { card ->
            val escapedName = escapeVCardText(card.displayName)
            buildString {
                append("BEGIN:VCARD\r\n")
                append("VERSION:3.0\r\n")
                append("N:$escapedName;;;;\r\n")
                append("FN:$escapedName\r\n")
                card.phoneNumbers.forEach { append("TEL;TYPE=CELL:${escapeVCardText(it)}\r\n") }
                card.emails.forEach { append("EMAIL;TYPE=HOME:${escapeVCardText(it)}\r\n") }
                append("END:VCARD\r\n")
            }
        }
        file.writeText(body)
        file.absolutePath
    }

    private class MutableCard(val contactId: Long, val displayName: String) {
        val rawContactIds = mutableListOf<Long>()
        val phoneNumbers = mutableListOf<String>()
        val emails = mutableListOf<String>()
    }
}

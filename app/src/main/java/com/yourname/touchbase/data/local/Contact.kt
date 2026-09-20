package com.yourname.touchbase.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncStatus { PENDING, SYNCED, CONFLICT }

/**
 * Shadow table over ContactsContract. We do NOT duplicate the system contact
 * as source of truth — systemContactId points back to the real Android contact,
 * and this table only carries app-specific metadata (tags, sync bookkeeping).
 *
 * systemContactId is nullable to support "quick add" contacts that haven't
 * been written into ContactsContract yet (see Phase 1 quick-add flow).
 */
@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systemContactId: Long? = null,
    val displayName: String,
    val phoneNumber: String,
    val rawTimestampAdded: Long,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)

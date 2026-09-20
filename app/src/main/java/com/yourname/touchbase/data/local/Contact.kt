package com.yourname.touchbase.data.local

import androidx.room.Entity
import androidx.room.Index
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
@Entity(
    tableName = "contacts",
    indices = [
        Index(value = ["systemContactId"]),
        Index(value = ["rawTimestampAdded"]),
        Index(value = ["lastSyncedTimestamp"])
    ]
)
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systemContactId: Long? = null,
    val displayName: String,
    val phoneNumber: String,
    // Stamped once on first sight, frozen after - see dev-log/DEVELOPMENT_LOG.md (2026-09-20).
    val rawTimestampAdded: Long,
    // Updated every time the OS reports this contact actually changed - unlike rawTimestampAdded, this is live.
    val lastSyncedTimestamp: Long = 0,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)

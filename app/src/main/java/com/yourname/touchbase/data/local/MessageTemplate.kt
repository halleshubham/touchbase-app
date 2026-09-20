package com.yourname.touchbase.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * bodyText supports {name} placeholder (extend with {event}, {date} etc.
 * as event-linking in Phase 3 matures).
 */
@Entity(tableName = "message_templates")
data class MessageTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bodyText: String,
    val isDefault: Boolean = false
)

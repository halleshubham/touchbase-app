package com.yourname.touchbase.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val colorHex: String = "#6750A4"
)

@Entity(
    tableName = "contact_tag_cross_ref",
    primaryKeys = ["contactId", "tagId"],
    indices = [Index(value = ["tagId"])]
)
data class ContactTagCrossRef(
    val contactId: Long,
    val tagId: Long
)

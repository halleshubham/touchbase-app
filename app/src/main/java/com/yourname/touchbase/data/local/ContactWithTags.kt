package com.yourname.touchbase.data.local

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class ContactWithTags(
    @Embedded val contact: Contact,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ContactTagCrossRef::class,
            parentColumn = "contactId",
            entityColumn = "tagId"
        )
    )
    val tags: List<Tag>
)

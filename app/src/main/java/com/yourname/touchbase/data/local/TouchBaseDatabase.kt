package com.yourname.touchbase.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        Contact::class,
        Tag::class,
        ContactTagCrossRef::class,
        MessageTemplate::class,
        Event::class,
        CallSession::class,
        CallSessionItem::class,
        SavedFilter::class
    ],
    version = 3,
    exportSchema = true
)
abstract class TouchBaseDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun messageTemplateDao(): MessageTemplateDao
    abstract fun eventDao(): EventDao
    abstract fun callSessionDao(): CallSessionDao
    abstract fun savedFilterDao(): SavedFilterDao
}

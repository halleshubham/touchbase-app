package com.yourname.touchbase.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class EventType { BIRTHDAY, FOLLOW_UP, CUSTOM }
enum class Recurrence { NONE, YEARLY, MONTHLY }

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val label: String,
    val eventType: EventType,
    val eventEpochMillis: Long,
    val recurrence: Recurrence = Recurrence.NONE,
    val reminderLeadMinutes: Long = 60,
    // WorkManager/AlarmManager request code used to cancel/reschedule this exact reminder.
    val alarmRequestCode: Int = 0
)

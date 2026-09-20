package com.yourname.touchbase.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.touchbase.data.local.ContactDao
import com.yourname.touchbase.data.local.Event
import com.yourname.touchbase.data.local.EventDao
import com.yourname.touchbase.data.local.EventType
import com.yourname.touchbase.data.local.Recurrence
import com.yourname.touchbase.reminders.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlin.random.Random
import javax.inject.Inject

@HiltViewModel
class EventViewModel @Inject constructor(
    private val eventDao: EventDao,
    private val contactDao: ContactDao,
    private val scheduler: ReminderScheduler
) : ViewModel() {

    fun observeForContact(contactId: Long) = eventDao.observeForContact(contactId)

    fun createEvent(
        contactId: Long,
        label: String,
        type: EventType,
        epochMillis: Long,
        recurrence: Recurrence,
        reminderLeadMinutes: Long
    ) {
        viewModelScope.launch {
            val contact = contactDao.findById(contactId) ?: return@launch
            val event = Event(
                contactId = contactId,
                label = label,
                eventType = type,
                eventEpochMillis = epochMillis,
                recurrence = recurrence,
                reminderLeadMinutes = reminderLeadMinutes,
                alarmRequestCode = Random.nextInt(1, Int.MAX_VALUE / 2)
            )
            val id = eventDao.upsert(event)
            scheduler.schedule(event.copy(id = id), contact.displayName, contact.phoneNumber)
        }
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            scheduler.cancel(event)
            eventDao.delete(event)
        }
    }
}

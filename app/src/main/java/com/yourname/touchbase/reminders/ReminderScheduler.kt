package com.yourname.touchbase.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.yourname.touchbase.data.local.Event
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

const val EXTRA_EVENT_ID = "extra_event_id"
const val EXTRA_CONTACT_ID = "extra_contact_id"
const val EXTRA_CONTACT_NAME = "extra_contact_name"
const val EXTRA_CONTACT_PHONE = "extra_contact_phone"
const val EXTRA_LABEL = "extra_label"

/**
 * Schedules/cancels exact alarms for event reminders. Each Event carries its
 * own alarmRequestCode so we can cancel and reschedule it precisely (e.g. if
 * the user edits the date or lead time) without touching other reminders.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(event: Event, contactName: String, contactPhone: String) {
        val triggerAt = event.eventEpochMillis - (event.reminderLeadMinutes * 60_000)
        if (triggerAt <= System.currentTimeMillis()) return // don't schedule reminders in the past

        val pendingIntent = buildPendingIntent(event, contactName, contactPhone)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Caller should have directed the user to grant SCHEDULE_EXACT_ALARM first;
            // fall back to an inexact alarm so the reminder still fires eventually.
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            return
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    fun cancel(event: Event) {
        alarmManager.cancel(buildPendingIntent(event, "", ""))
    }

    private fun buildPendingIntent(event: Event, contactName: String, contactPhone: String): PendingIntent {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra(EXTRA_EVENT_ID, event.id)
            putExtra(EXTRA_CONTACT_ID, event.contactId)
            putExtra(EXTRA_CONTACT_NAME, contactName)
            putExtra(EXTRA_CONTACT_PHONE, contactPhone)
            putExtra(EXTRA_LABEL, event.label)
        }
        return PendingIntent.getBroadcast(
            context,
            event.alarmRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

package com.yourname.touchbase.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yourname.touchbase.data.local.ContactDao
import com.yourname.touchbase.data.local.EventDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Exact alarms set with AlarmManager do NOT survive a device reboot, so every
 * future event's reminder must be re-scheduled once the system comes back up.
 */
@AndroidEntryPoint
class BootReminderRescheduler : BroadcastReceiver() {

    @Inject lateinit var eventDao: EventDao
    @Inject lateinit var contactDao: ContactDao
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val upcoming = eventDao.getUpcoming(System.currentTimeMillis())
                upcoming.forEach { event ->
                    val contact = contactDao.findById(event.contactId)
                    scheduler.schedule(
                        event,
                        contact?.displayName ?: "Contact",
                        contact?.phoneNumber ?: ""
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

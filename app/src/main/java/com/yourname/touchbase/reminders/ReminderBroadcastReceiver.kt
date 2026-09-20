package com.yourname.touchbase.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yourname.touchbase.MainActivity
import com.yourname.touchbase.data.local.EventDao
import com.yourname.touchbase.data.local.Recurrence
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

const val REMINDER_CHANNEL_ID = "reminders"

@AndroidEntryPoint
class ReminderBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var eventDao: EventDao
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1)
        val contactName = intent.getStringExtra(EXTRA_CONTACT_NAME) ?: "Contact"
        val contactPhone = intent.getStringExtra(EXTRA_CONTACT_PHONE) ?: ""
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Reminder"

        // Tapping the notification body opens the app.
        val openAppIntent = PendingIntent.getActivity(
            context, eventId.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // The "Call" action dials directly via ACTION_DIAL, which opens the dialer
        // pre-filled — no CALL_PHONE permission required and no risk of a silent
        // accidental dial straight from a notification tap.
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$contactPhone"))
        val callAction = PendingIntent.getActivity(
            context, eventId.toInt() + 100_000, dialIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("$label — $contactName")
            .setContentText("Tap to call or open TouchBase")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_menu_call, "Call", callAction)
            .build()

        NotificationManagerCompat.from(context).notify(eventId.toInt(), notification)

        // Recurring events (birthdays, monthly check-ins): queue the next occurrence.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val event = eventDao.getById(eventId) ?: return@launch
                if (event.recurrence == Recurrence.NONE) return@launch

                val next = Calendar.getInstance().apply {
                    timeInMillis = event.eventEpochMillis
                    when (event.recurrence) {
                        Recurrence.YEARLY -> add(Calendar.YEAR, 1)
                        Recurrence.MONTHLY -> add(Calendar.MONTH, 1)
                        Recurrence.NONE -> {}
                    }
                }.timeInMillis

                val updated = event.copy(eventEpochMillis = next)
                eventDao.upsert(updated)
                scheduler.schedule(updated, contactName, contactPhone)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

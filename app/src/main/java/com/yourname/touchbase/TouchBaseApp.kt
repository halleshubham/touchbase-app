package com.yourname.touchbase

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.yourname.touchbase.reminders.REMINDER_CHANNEL_ID
import com.yourname.touchbase.sync.ContactsContentObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TouchBaseApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var contactsContentObserver: ContactsContentObserver

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // Starts watching ContactsContract immediately so any change — from
        // this app or the system Contacts app — triggers a near-real-time
        // sync (see ContactsContentObserver). No-ops harmlessly if the user
        // hasn't signed into Google yet; SyncWorker checks sign-in status.
        contactsContentObserver.startWatching()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                "Call reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Reminders to call contacts for upcoming events" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

package com.yourname.touchbase.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers on ContactsContract.CONTENT_URI so any addition/edit/deletion —
 * whether made inside this app or in the system Contacts app — enqueues an
 * immediate one-off sync. This is what makes sync feel "near real-time"
 * rather than only running on a periodic timer.
 */
@Singleton
class ContactsContentObserver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var observer: ContentObserver? = null

    fun startWatching() {
        if (observer != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val handler = Handler(Looper.getMainLooper())
        val obs = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                enqueueImmediateSync()
            }
        }
        context.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            obs
        )
        observer = obs
    }

    fun stopWatching() {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
    }

    private fun enqueueImmediateSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE, // a newer change supersedes a queued-but-not-yet-run sync
            request
        )
    }
}

package com.yourname.touchbase.sync

import android.content.Context
import android.content.ContentResolver
import android.os.Bundle
import android.provider.ContactsContract
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Doesn't talk to any Google API directly — just asks Android's existing
 * account sync adapter to run now instead of waiting for its normal
 * schedule. This is the same thing tapping "Sync now" on an account in
 * Settings > Accounts does.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val accountsHelper: AccountsHelper
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "touchbase_device_sync"
    }

    override suspend fun doWork(): Result {
        val accounts = accountsHelper.listGoogleAccounts()
        if (accounts.isEmpty()) return Result.success() // nothing to sync

        val extras = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }
        accounts.forEach { account ->
            ContentResolver.requestSync(account, ContactsContract.AUTHORITY, extras)
        }
        return Result.success()
    }
}

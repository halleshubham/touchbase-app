package com.yourname.touchbase.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** com.google is Android's account type for Google accounts added under Settings > Accounts. */
const val GOOGLE_ACCOUNT_TYPE = "com.google"

/**
 * Represents where a contact should be written: an existing synced Google
 * account, or [PHONE_ONLY] for a device-local contact with no sync adapter
 * behind it at all (never leaves the phone).
 */
sealed class ContactAccount {
    abstract val displayLabel: String

    object PhoneOnly : ContactAccount() {
        override val displayLabel = "Phone only (no sync)"
    }

    data class Google(val account: Account) : ContactAccount() {
        override val displayLabel: String = account.name
    }
}

@Singleton
class AccountsHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy { context.getSharedPreferences("touchbase_sync_prefs", Context.MODE_PRIVATE) }

    /**
     * Lists every Google account currently signed into this device. Requires
     * GET_ACCOUNTS (or, on API 26+, having READ_CONTACTS already grants
     * visibility into accounts that own contacts data — we request both to
     * be safe across versions).
     */
    fun listGoogleAccounts(): List<Account> {
        val manager = AccountManager.get(context)
        return runCatching {
            manager.getAccountsByType(GOOGLE_ACCOUNT_TYPE).toList()
        }.getOrDefault(emptyList())
    }

    fun listAllOptions(): List<ContactAccount> =
        listOf(ContactAccount.PhoneOnly) + listGoogleAccounts().map { ContactAccount.Google(it) }

    /** The account quick-add should default to, remembered across sessions. */
    fun getPreferredAccount(): ContactAccount {
        val savedName = prefs.getString(KEY_PREFERRED_ACCOUNT, null) ?: return ContactAccount.PhoneOnly
        val match = listGoogleAccounts().firstOrNull { it.name == savedName }
        return match?.let { ContactAccount.Google(it) } ?: ContactAccount.PhoneOnly
    }

    fun setPreferredAccount(account: ContactAccount) {
        prefs.edit().putString(
            KEY_PREFERRED_ACCOUNT,
            if (account is ContactAccount.Google) account.account.name else null
        ).apply()
    }

    private companion object {
        const val KEY_PREFERRED_ACCOUNT = "preferred_account_name"
    }
}

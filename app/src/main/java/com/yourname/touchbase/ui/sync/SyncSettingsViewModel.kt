package com.yourname.touchbase.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yourname.touchbase.sync.AccountsHelper
import com.yourname.touchbase.sync.ContactAccount
import com.yourname.touchbase.sync.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class SyncSettingsUiState(
    val options: List<ContactAccount> = listOf(ContactAccount.PhoneOnly),
    val selected: ContactAccount = ContactAccount.PhoneOnly,
    val syncQueued: Boolean = false
)

@HiltViewModel
class SyncSettingsViewModel @Inject constructor(
    private val accountsHelper: AccountsHelper,
    private val workManager: WorkManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SyncSettingsUiState(
            options = accountsHelper.listAllOptions(),
            selected = accountsHelper.getPreferredAccount()
        )
    )
    val uiState: StateFlow<SyncSettingsUiState> = _uiState.asStateFlow()

    fun refreshAccounts() {
        _uiState.value = _uiState.value.copy(options = accountsHelper.listAllOptions())
    }

    fun selectAccount(account: ContactAccount) {
        accountsHelper.setPreferredAccount(account)
        _uiState.value = _uiState.value.copy(selected = account)
    }

    fun triggerManualSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
        workManager.enqueueUniqueWork(SyncWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        _uiState.value = _uiState.value.copy(syncQueued = true)
    }
}

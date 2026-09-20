package com.yourname.touchbase.ui.callqueue

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.touchbase.data.local.CallSessionDao
import com.yourname.touchbase.data.local.QueueRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// Surfaces CallSessionDao.observeHistoryForContact(), which existed unused - see dev-log/DEVELOPMENT_LOG.md (2026-09-20).
@HiltViewModel
class ContactHistoryViewModel @Inject constructor(
    dao: CallSessionDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val contactId: Long = savedStateHandle.get<Long>("contactId") ?: 0L

    val history: StateFlow<List<QueueRow>> =
        dao.observeHistoryForContact(contactId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

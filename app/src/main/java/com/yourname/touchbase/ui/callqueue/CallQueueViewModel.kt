package com.yourname.touchbase.ui.callqueue

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.touchbase.data.local.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallQueueViewModel @Inject constructor(
    private val dao: CallSessionDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L

    val queue: StateFlow<List<QueueRow>> =
        dao.observeQueue(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Creates a new session from a list of contact ids (already filtered by the caller) and returns its id. */
    suspend fun startSession(contactIds: List<Long>, filterDescription: String): Long {
        val id = dao.insertSession(CallSession(filterDescription = filterDescription))
        dao.insertItems(
            contactIds.mapIndexed { index, contactId ->
                CallSessionItem(sessionId = id, contactId = contactId, orderIndex = index)
            }
        )
        return id
    }

    fun markCalled(item: CallSessionItem) {
        viewModelScope.launch {
            dao.updateItem(item.copy(outcome = CallOutcome.CALLED, calledAtEpochMillis = System.currentTimeMillis()))
        }
    }

    fun markSkipped(item: CallSessionItem) {
        viewModelScope.launch { dao.updateItem(item.copy(outcome = CallOutcome.SKIPPED)) }
    }

    fun recordFeedback(item: CallSessionItem, feedback: CallFeedback, note: String?) {
        viewModelScope.launch {
            dao.updateItem(item.copy(feedback = feedback, feedbackNote = note))
        }
    }

    fun completeSessionIfDone() {
        viewModelScope.launch {
            val remaining = queue.value.count { it.item.outcome == CallOutcome.PENDING }
            if (remaining == 0) {
                dao.getSession(sessionId)?.let {
                    dao.updateSession(it.copy(status = SessionStatus.COMPLETED))
                }
            }
        }
    }
}

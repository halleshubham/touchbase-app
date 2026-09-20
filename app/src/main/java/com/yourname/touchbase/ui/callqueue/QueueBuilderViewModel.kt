package com.yourname.touchbase.ui.callqueue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.touchbase.data.local.ContactWithTags
import com.yourname.touchbase.data.local.Tag
import com.yourname.touchbase.data.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class QueueBuilderUiState(
    val contacts: List<ContactWithTags> = emptyList(),
    val allTags: List<Tag> = emptyList()
)

/**
 * Needs the full contact list (not paged) - building a queue means resolving
 * every contact id matching the chosen tag before starting a session.
 */
@HiltViewModel
class QueueBuilderViewModel @Inject constructor(
    repository: ContactRepository
) : ViewModel() {

    val uiState: StateFlow<QueueBuilderUiState> = combine(
        repository.observeContacts(),
        repository.observeTags()
    ) { contacts, tags ->
        QueueBuilderUiState(contacts = contacts, allTags = tags)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QueueBuilderUiState())
}

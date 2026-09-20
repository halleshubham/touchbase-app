package com.yourname.touchbase.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.touchbase.data.local.ContactWithTags
import com.yourname.touchbase.data.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateListUiState(
    val query: String = "",
    val results: List<ContactWithTags> = emptyList(),
    val selectedContactIds: Set<Long> = emptySet(),
    val created: Boolean = false
)

// Needs the full contact list, not paged - search has to scan everything, not one page at a time.
@HiltViewModel
class CreateListViewModel @Inject constructor(
    private val repository: ContactRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _selectedContactIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _created = MutableStateFlow(false)

    val uiState: StateFlow<CreateListUiState> = combine(
        repository.observeContacts(),
        _query,
        _selectedContactIds,
        _created
    ) { contacts, query, selected, created ->
        val results = if (query.isBlank()) contacts
        else contacts.filter {
            it.contact.displayName.contains(query, ignoreCase = true) ||
                it.contact.phoneNumber.contains(query)
        }
        CreateListUiState(query, results, selected, created)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CreateListUiState())

    fun setQuery(query: String) {
        _query.value = query
    }

    fun toggleContact(contactId: Long) {
        _selectedContactIds.value = _selectedContactIds.value.let {
            if (contactId in it) it - contactId else it + contactId
        }
    }

    fun createList(name: String) {
        val ids = _selectedContactIds.value
        if (ids.isEmpty() || name.isBlank()) return
        viewModelScope.launch {
            repository.createTagWithContacts(name, ids.toList())
            _created.value = true
        }
    }
}

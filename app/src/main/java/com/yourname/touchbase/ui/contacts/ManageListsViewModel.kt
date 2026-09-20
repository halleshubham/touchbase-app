package com.yourname.touchbase.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.touchbase.data.local.SavedFilter
import com.yourname.touchbase.data.local.Tag
import com.yourname.touchbase.data.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManageListsUiState(val tags: List<Tag> = emptyList(), val savedFilters: List<SavedFilter> = emptyList())

@HiltViewModel
class ManageListsViewModel @Inject constructor(private val repository: ContactRepository) : ViewModel() {

    val uiState: StateFlow<ManageListsUiState> = combine(
        repository.observeTags(),
        repository.observeSavedFilters()
    ) { tags, filters -> ManageListsUiState(tags, filters) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ManageListsUiState())

    fun renameTag(tagId: Long, label: String) {
        viewModelScope.launch { repository.renameTag(tagId, label) }
    }

    fun deleteTag(tagId: Long) {
        viewModelScope.launch { repository.deleteTag(tagId) }
    }

    fun renameSavedFilter(filter: SavedFilter, name: String) {
        viewModelScope.launch { repository.renameSavedFilter(filter, name) }
    }

    fun deleteSavedFilter(filter: SavedFilter) {
        viewModelScope.launch { repository.deleteFilter(filter) }
    }
}

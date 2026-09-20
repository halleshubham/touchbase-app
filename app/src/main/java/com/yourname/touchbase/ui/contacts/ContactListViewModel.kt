package com.yourname.touchbase.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.touchbase.data.local.ContactWithTags
import com.yourname.touchbase.data.local.MessageTemplate
import com.yourname.touchbase.data.local.MessageTemplateDao
import com.yourname.touchbase.data.local.Tag
import com.yourname.touchbase.data.repository.ContactRepository
import com.yourname.touchbase.sync.AccountsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactListUiState(
    val contacts: List<ContactWithTags> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val templates: List<MessageTemplate> = emptyList(),
    val isLoading: Boolean = true,
    val selectedTagFilter: Long? = null,
    val quickAddAccountLabel: String = "Phone only (no sync)"
)

@HiltViewModel
class ContactListViewModel @Inject constructor(
    private val repository: ContactRepository,
    private val templateDao: MessageTemplateDao,
    private val accountsHelper: AccountsHelper
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    private val _tagFilter = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<ContactListUiState> = combine(
        repository.observeContacts(),
        repository.observeTags(),
        templateDao.observeAll(),
        _isLoading,
        _tagFilter
    ) { contacts, tags, templates, loading, filter ->
        val filtered = if (filter == null) contacts
        else contacts.filter { cwt -> cwt.tags.any { it.id == filter } }
        ContactListUiState(
            contacts = filtered,
            allTags = tags,
            templates = templates,
            isLoading = loading,
            selectedTagFilter = filter,
            quickAddAccountLabel = accountsHelper.getPreferredAccount().displayLabel
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContactListUiState())

    fun refreshFromSystemContacts() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.refreshFromSystemContacts()
            _isLoading.value = false
        }
    }

    fun setTagFilter(tagId: Long?) {
        _tagFilter.value = tagId
    }

    fun quickAdd(name: String, phone: String) {
        viewModelScope.launch {
            val preferred = accountsHelper.getPreferredAccount()
            val account = (preferred as? com.yourname.touchbase.sync.ContactAccount.Google)?.account
            repository.quickAdd(name, phone, account)
        }
    }

    fun createTag(label: String) {
        viewModelScope.launch { repository.createTag(label) }
    }

    fun toggleTag(contactId: Long, tag: Tag, isCurrentlyAssigned: Boolean) {
        viewModelScope.launch {
            if (isCurrentlyAssigned) repository.removeTag(contactId, tag.id)
            else repository.assignTag(contactId, tag.id)
        }
    }
}

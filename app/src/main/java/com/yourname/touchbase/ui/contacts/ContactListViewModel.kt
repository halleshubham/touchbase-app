package com.yourname.touchbase.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.touchbase.data.local.ContactWithTags
import com.yourname.touchbase.data.local.MessageTemplate
import com.yourname.touchbase.data.local.MessageTemplateDao
import com.yourname.touchbase.data.local.Tag
import com.yourname.touchbase.data.repository.ContactRepository
import com.yourname.touchbase.sync.AccountsHelper
import com.yourname.touchbase.sync.ContactsContentObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOrder { NEWEST_ADDED_FIRST, OLDEST_ADDED_FIRST }

enum class DateFilter(val label: String, val maxAgeDays: Int?) {
    ALL_TIME("All time", null),
    LAST_7_DAYS("Last 7 days", 7),
    LAST_30_DAYS("Last 30 days", 30),
    LAST_90_DAYS("Last 90 days", 90)
}

private data class ListControls(
    val tagFilter: Long?,
    val sortOrder: SortOrder,
    val dateFilter: DateFilter
)

data class ContactListUiState(
    val contacts: List<ContactWithTags> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val templates: List<MessageTemplate> = emptyList(),
    val isLoading: Boolean = true,
    val selectedTagFilter: Long? = null,
    val sortOrder: SortOrder = SortOrder.NEWEST_ADDED_FIRST,
    val dateFilter: DateFilter = DateFilter.ALL_TIME,
    val quickAddAccountLabel: String = "Phone only (no sync)"
)

@HiltViewModel
class ContactListViewModel @Inject constructor(
    private val repository: ContactRepository,
    private val templateDao: MessageTemplateDao,
    private val accountsHelper: AccountsHelper,
    private val contactsContentObserver: ContactsContentObserver
) : ViewModel() {

    init {
        // Safe to start here (unlike in TouchBaseApp.onCreate): this
        // ViewModel is only ever constructed inside ContactsPermissionGate,
        // so READ_CONTACTS/WRITE_CONTACTS are already granted by this point.
        contactsContentObserver.startWatching()
    }

    private val _isLoading = MutableStateFlow(true)
    private val _tagFilter = MutableStateFlow<Long?>(null)
    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST_ADDED_FIRST)
    private val _dateFilter = MutableStateFlow(DateFilter.ALL_TIME)

    private val controls = combine(_tagFilter, _sortOrder, _dateFilter) { tagFilter, sortOrder, dateFilter ->
        ListControls(tagFilter, sortOrder, dateFilter)
    }

    val uiState: StateFlow<ContactListUiState> = combine(
        repository.observeContacts(),
        repository.observeTags(),
        templateDao.observeAll(),
        _isLoading,
        controls
    ) { contacts, tags, templates, loading, controls ->
        val tagFiltered = if (controls.tagFilter == null) contacts
        else contacts.filter { cwt -> cwt.tags.any { it.id == controls.tagFilter } }

        val dateFiltered = controls.dateFilter.maxAgeDays?.let { days ->
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
            tagFiltered.filter { it.contact.rawTimestampAdded >= cutoff }
        } ?: tagFiltered

        val sorted = when (controls.sortOrder) {
            SortOrder.NEWEST_ADDED_FIRST -> dateFiltered.sortedByDescending { it.contact.rawTimestampAdded }
            SortOrder.OLDEST_ADDED_FIRST -> dateFiltered.sortedBy { it.contact.rawTimestampAdded }
        }

        ContactListUiState(
            contacts = sorted,
            allTags = tags,
            templates = templates,
            isLoading = loading,
            selectedTagFilter = controls.tagFilter,
            sortOrder = controls.sortOrder,
            dateFilter = controls.dateFilter,
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

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setDateFilter(filter: DateFilter) {
        _dateFilter.value = filter
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

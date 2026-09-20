package com.yourname.touchbase.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yourname.touchbase.data.local.ContactWithTags
import com.yourname.touchbase.data.local.MessageTemplate
import com.yourname.touchbase.data.local.MessageTemplateDao
import com.yourname.touchbase.data.local.SavedFilter
import com.yourname.touchbase.data.local.Tag
import com.yourname.touchbase.data.repository.ContactRepository
import com.yourname.touchbase.sync.AccountsHelper
import com.yourname.touchbase.sync.ContactsContentObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOrder { NEWEST_FIRST, OLDEST_FIRST }

enum class DateBasis(val label: String) { ADDED("Added"), SYNCED("Last synced") }

enum class DateFilter(val label: String, val maxAgeDays: Int?) {
    ALL_TIME("All", null),
    LAST_WEEK("Last week", 7),
    LAST_MONTH("Last month", 30),
    LAST_3_MONTHS("Last 3 months", 90),
    CUSTOM("Custom range", null)
}

private data class ListControls(
    val tagFilter: Long?,
    val sortOrder: SortOrder,
    val dateFilter: DateFilter,
    val dateBasis: DateBasis,
    val customRange: Pair<Long, Long>?
)

data class ContactListUiState(
    val allTags: List<Tag> = emptyList(),
    val templates: List<MessageTemplate> = emptyList(),
    val isLoading: Boolean = true,
    val selectedTagFilter: Long? = null,
    val sortOrder: SortOrder = SortOrder.NEWEST_FIRST,
    val dateFilter: DateFilter = DateFilter.ALL_TIME,
    val dateBasis: DateBasis = DateBasis.ADDED,
    val customRange: Pair<Long, Long>? = null,
    val savedFilters: List<SavedFilter> = emptyList(),
    val quickAddAccountLabel: String = "Phone only (no sync)"
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactListViewModel @Inject constructor(
    private val repository: ContactRepository,
    private val templateDao: MessageTemplateDao,
    private val accountsHelper: AccountsHelper,
    private val contactsContentObserver: ContactsContentObserver
) : ViewModel() {

    init {
        // Safe here since this VM only exists behind ContactsPermissionGate - see dev-log/DEVELOPMENT_LOG.md (2026-09-20).
        contactsContentObserver.startWatching()
    }

    private val _isLoading = MutableStateFlow(true)
    private val _tagFilter = MutableStateFlow<Long?>(null)
    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST_FIRST)
    private val _dateFilter = MutableStateFlow(DateFilter.ALL_TIME)
    private val _dateBasis = MutableStateFlow(DateBasis.ADDED)
    private val _customRange = MutableStateFlow<Pair<Long, Long>?>(null)

    private val controls = combine(
        _tagFilter, _sortOrder, _dateFilter, _dateBasis, _customRange
    ) { tagFilter, sortOrder, dateFilter, dateBasis, customRange ->
        ListControls(tagFilter, sortOrder, dateFilter, dateBasis, customRange)
    }

    // Rebuilds the Pager per filter/sort change - see dev-log/DEVELOPMENT_LOG.md (2026-09-20).
    val pagedContacts: Flow<PagingData<ContactWithTags>> = controls
        .flatMapLatest { c ->
            val (minTimestamp, maxTimestamp) = when {
                c.dateFilter == DateFilter.CUSTOM -> c.customRange?.first to c.customRange?.second
                c.dateFilter.maxAgeDays != null ->
                    (System.currentTimeMillis() - TimeUnit.DAYS.toMillis(c.dateFilter.maxAgeDays.toLong())) to null
                else -> null to null
            }
            Pager(
                config = PagingConfig(pageSize = 30, enablePlaceholders = false)
            ) {
                repository.pagedContacts(
                    tagId = c.tagFilter,
                    minTimestamp = minTimestamp,
                    maxTimestamp = maxTimestamp,
                    sortAscending = c.sortOrder == SortOrder.OLDEST_FIRST,
                    useSyncedBasis = c.dateBasis == DateBasis.SYNCED
                )
            }.flow
        }
        .cachedIn(viewModelScope)

    val uiState: StateFlow<ContactListUiState> = combine(
        repository.observeTags(),
        templateDao.observeAll(),
        _isLoading,
        controls,
        repository.observeSavedFilters()
    ) { tags, templates, loading, controls, savedFilters ->
        ContactListUiState(
            allTags = tags,
            templates = templates,
            isLoading = loading,
            selectedTagFilter = controls.tagFilter,
            sortOrder = controls.sortOrder,
            dateFilter = controls.dateFilter,
            dateBasis = controls.dateBasis,
            customRange = controls.customRange,
            savedFilters = savedFilters,
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

    fun setDateBasis(basis: DateBasis) {
        _dateBasis.value = basis
    }

    fun setDateFilter(filter: DateFilter) {
        _dateFilter.value = filter
        if (filter != DateFilter.CUSTOM) _customRange.value = null
    }

    fun setCustomDateRange(startMillis: Long, endMillis: Long) {
        _customRange.value = startMillis to endMillis
        _dateFilter.value = DateFilter.CUSTOM
    }

    /** Saves the current tag/date/sort combination as a named, reusable list. */
    fun saveCurrentFilterAsList(name: String) {
        viewModelScope.launch {
            repository.saveFilter(
                name = name,
                tagId = _tagFilter.value,
                dateFilterName = _dateFilter.value.name,
                sortOrderName = _sortOrder.value.name,
                dateBasisName = _dateBasis.value.name,
                customRangeStart = _customRange.value?.first,
                customRangeEnd = _customRange.value?.second
            )
        }
    }

    fun applySavedFilter(filter: SavedFilter) {
        _tagFilter.value = filter.tagId
        _dateFilter.value = DateFilter.valueOf(filter.dateFilter)
        _sortOrder.value = SortOrder.valueOf(filter.sortOrder)
        _dateBasis.value = DateBasis.valueOf(filter.dateBasis)
        _customRange.value = if (filter.customRangeStart != null && filter.customRangeEnd != null) {
            filter.customRangeStart to filter.customRangeEnd
        } else {
            null
        }
    }

    fun deleteSavedFilter(filter: SavedFilter) {
        viewModelScope.launch { repository.deleteFilter(filter) }
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

    fun createTagAndAssign(contactId: Long, label: String) {
        viewModelScope.launch {
            val tagId = repository.createTag(label)
            repository.assignTag(contactId, tagId)
        }
    }

    fun toggleTag(contactId: Long, tag: Tag, isCurrentlyAssigned: Boolean) {
        viewModelScope.launch {
            if (isCurrentlyAssigned) repository.removeTag(contactId, tag.id)
            else repository.assignTag(contactId, tag.id)
        }
    }

    fun renameTag(tagId: Long, label: String) {
        viewModelScope.launch { repository.renameTag(tagId, label) }
    }

    fun deleteTag(tagId: Long) {
        viewModelScope.launch {
            if (_tagFilter.value == tagId) _tagFilter.value = null
            repository.deleteTag(tagId)
        }
    }

    fun renameSavedFilter(filter: SavedFilter, newName: String) {
        viewModelScope.launch { repository.renameSavedFilter(filter, newName) }
    }
}

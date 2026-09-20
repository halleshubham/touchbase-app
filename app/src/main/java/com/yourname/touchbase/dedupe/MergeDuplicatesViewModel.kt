package com.yourname.touchbase.dedupe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yourname.touchbase.sync.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MergeDuplicatesUiState {
    data object Idle : MergeDuplicatesUiState
    data object Scanning : MergeDuplicatesUiState
    data class Reviewing(val groups: List<DuplicateGroup>, val selected: Set<Int>) : MergeDuplicatesUiState
    data class Merging(val done: Int, val total: Int) : MergeDuplicatesUiState
    data class Done(val mergedCount: Int, val removedCount: Int, val backupPath: String) : MergeDuplicatesUiState
    data class Error(val message: String, val mergedSoFar: Int, val backupPath: String) : MergeDuplicatesUiState
}

@HiltViewModel
class MergeDuplicatesViewModel @Inject constructor(
    private val scanner: DuplicateContactsScanner,
    private val workManager: WorkManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<MergeDuplicatesUiState>(MergeDuplicatesUiState.Idle)
    val uiState: StateFlow<MergeDuplicatesUiState> = _uiState.asStateFlow()

    fun scan() {
        viewModelScope.launch {
            _uiState.value = MergeDuplicatesUiState.Scanning
            val groups = scanner.scan()
            _uiState.value = MergeDuplicatesUiState.Reviewing(groups, selected = groups.indices.toSet())
        }
    }

    fun toggleGroup(index: Int) {
        val state = _uiState.value as? MergeDuplicatesUiState.Reviewing ?: return
        val newSelected = if (index in state.selected) state.selected - index else state.selected + index
        _uiState.value = state.copy(selected = newSelected)
    }

    fun mergeSelected() {
        val state = _uiState.value as? MergeDuplicatesUiState.Reviewing ?: return
        val total = state.selected.size
        viewModelScope.launch {
            _uiState.value = MergeDuplicatesUiState.Merging(0, total)
            // One backup file for the whole batch, written before any group is touched - see dev-log/DEVELOPMENT_LOG.md (2026-09-20).
            val backupPath = scanner.writeBackup(state.selected.flatMap { state.groups[it].cards })
            var removedCount = 0
            var mergedCount = 0
            try {
                state.selected.forEach { index ->
                    val outcome = scanner.mergeGroup(state.groups[index])
                    removedCount += outcome.removedContactIds.size
                    mergedCount++
                    _uiState.value = MergeDuplicatesUiState.Merging(mergedCount, total)
                }
                requestGoogleSync()
                _uiState.value = MergeDuplicatesUiState.Done(mergedCount, removedCount, backupPath)
            } catch (e: Exception) {
                _uiState.value = MergeDuplicatesUiState.Error(e.message ?: "Unknown error", mergedCount, backupPath)
            }
        }
    }

    private fun requestGoogleSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
        workManager.enqueueUniqueWork(SyncWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun reset() {
        _uiState.value = MergeDuplicatesUiState.Idle
    }
}

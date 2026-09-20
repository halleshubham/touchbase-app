package com.yourname.touchbase.dedupe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeDuplicatesScreen(onBack: () -> Unit, viewModel: MergeDuplicatesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Merge duplicate contacts") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is MergeDuplicatesUiState.Idle -> IdleContent(onScan = viewModel::scan)
                is MergeDuplicatesUiState.Scanning -> LoadingContent("Scanning your phone contacts…")
                is MergeDuplicatesUiState.Reviewing -> ReviewContent(
                    state = s,
                    onToggle = viewModel::toggleGroup,
                    onMerge = viewModel::mergeSelected
                )
                is MergeDuplicatesUiState.Merging -> LoadingContent(
                    if (s.done == 0) "Writing backup…" else "Merging group ${s.done} of ${s.total}…"
                )
                is MergeDuplicatesUiState.Done -> DoneContent(s, onDone = onBack)
                is MergeDuplicatesUiState.Error -> ErrorContent(s, onDone = onBack)
            }
        }
    }
}

@Composable
private fun IdleContent(onScan: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Scans every phone contact for ones that share a phone number but " +
                "weren't already combined by Android - usually because their " +
                "saved names differ. You'll review each group and choose what " +
                "to merge before anything changes. A re-importable backup " +
                "(.vcf, restorable via Contacts app > Import) of every " +
                "contact involved is written to your device before any deletion.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onScan) { Text("Scan for duplicates") }
    }
}

@Composable
private fun LoadingContent(label: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReviewContent(
    state: MergeDuplicatesUiState.Reviewing,
    onToggle: (Int) -> Unit,
    onMerge: () -> Unit
) {
    if (state.groups.isEmpty()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("No duplicate contacts found.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "${state.groups.size} duplicate group(s) found. Uncheck any you don't want to merge.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
        LazyColumn(Modifier.weight(1f)) {
            items(state.groups.size) { index ->
                val group = state.groups[index]
                ListItem(
                    headlineContent = { Text(group.cards.joinToString(" + ") { it.displayName }) },
                    supportingContent = {
                        Text(group.cards.joinToString("  •  ") { it.phoneNumbers.firstOrNull() ?: "no number" })
                    },
                    leadingContent = {
                        Checkbox(checked = index in state.selected, onCheckedChange = { onToggle(index) })
                    }
                )
                HorizontalDivider()
            }
        }
        Button(
            onClick = onMerge,
            enabled = state.selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Text("Merge ${state.selected.size} selected group(s)")
        }
    }
}

@Composable
private fun DoneContent(state: MergeDuplicatesUiState.Done, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Merged ${state.mergedCount} group(s), removed ${state.removedCount} duplicate contact(s).", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Backup (re-importable via Contacts app > Import) written to: ${state.backupPath}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Asked Google to sync now, but that only requests a sync - it isn't instant.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onDone) { Text("Done") }
    }
}

@Composable
private fun ErrorContent(state: MergeDuplicatesUiState.Error, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Merge stopped after an error", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(state.message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "${state.mergedSoFar} group(s) were already merged before this happened - " +
                "backup written to: ${state.backupPath}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onDone) { Text("Done") }
    }
}

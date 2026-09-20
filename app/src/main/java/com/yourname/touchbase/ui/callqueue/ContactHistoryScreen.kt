package com.yourname.touchbase.ui.callqueue

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
import com.yourname.touchbase.data.local.CallOutcome
import com.yourname.touchbase.data.local.QueueRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val historyDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactHistoryScreen(
    contactName: String,
    onBack: () -> Unit,
    viewModel: ContactHistoryViewModel = hiltViewModel()
) {
    val history by viewModel.history.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$contactName - call history") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No calls logged yet for this contact.")
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(history, key = { it.item.id }) { row -> HistoryRow(row) }
            }
        }
    }
}

@Composable
private fun HistoryRow(row: QueueRow) {
    ListItem(
        headlineContent = { Text(outcomeLabel(row.item.outcome)) },
        supportingContent = {
            Column {
                row.item.calledAtEpochMillis?.let {
                    Text(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(historyDateFormatter))
                }
                if (row.item.feedback.name != "NONE") Text("Feedback: ${feedbackLabel(row.item.feedback.name)}")
                row.item.feedbackNote?.let { Text("Note: $it") }
            }
        }
    )
    HorizontalDivider()
}

private fun outcomeLabel(outcome: CallOutcome) = when (outcome) {
    CallOutcome.PENDING -> "Pending"
    CallOutcome.CALLED -> "Called"
    CallOutcome.SKIPPED -> "Skipped"
    CallOutcome.FAILED -> "Failed"
}

private fun feedbackLabel(name: String) = name.lowercase().replace('_', ' ')
    .replaceFirstChar { it.uppercase() }

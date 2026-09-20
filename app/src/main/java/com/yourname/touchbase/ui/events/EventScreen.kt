package com.yourname.touchbase.ui.events

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yourname.touchbase.data.local.EventType
import com.yourname.touchbase.data.local.Recurrence
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventScreen(
    contactId: Long,
    contactName: String,
    onBack: () -> Unit,
    viewModel: EventViewModel = hiltViewModel()
) {
    val events by viewModel.observeForContact(contactId).collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminders for $contactName") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add reminder")
            }
        }
    ) { padding ->
        if (events.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No reminders yet for $contactName.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(events, key = { it.id }) { event ->
                    ListItem(
                        headlineContent = { Text(event.label) },
                        supportingContent = {
                            Text("${dateFormat.format(Date(event.eventEpochMillis))} · reminder ${event.reminderLeadMinutes} min before")
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.deleteEvent(event) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAdd) {
        AddEventDialog(
            onDismiss = { showAdd = false },
            onConfirm = { label, type, epochMillis, recurrence, leadMinutes ->
                viewModel.createEvent(contactId, label, type, epochMillis, recurrence, leadMinutes)
                showAdd = false
            }
        )
    }
}

@Composable
private fun AddEventDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, EventType, Long, Recurrence, Long) -> Unit
) {
    var label by remember { mutableStateOf("") }
    // Default: 7 days from now, a reasonable starting point the user adjusts.
    var epochMillis by remember { mutableStateOf(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L) }
    var leadMinutesText by remember { mutableStateOf("60") }
    var recurrence by remember { mutableStateOf(Recurrence.NONE) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("What's this for? (e.g. Birthday)") })
                Text("Date: ${dateFormat.format(Date(epochMillis))}", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { epochMillis += 24 * 60 * 60 * 1000L }) { Text("+1 day") }
                    TextButton(onClick = { epochMillis += 7 * 24 * 60 * 60 * 1000L }) { Text("+1 week") }
                    TextButton(onClick = { epochMillis += 30L * 24 * 60 * 60 * 1000L }) { Text("+1 month") }
                }
                OutlinedTextField(
                    value = leadMinutesText,
                    onValueChange = { leadMinutesText = it.filter { c -> c.isDigit() } },
                    label = { Text("Remind me this many minutes before") }
                )
                Text("Repeat", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Recurrence.entries.forEach { option ->
                        FilterChip(
                            selected = recurrence == option,
                            onClick = { recurrence = option },
                            label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (label.isNotBlank()) {
                    onConfirm(label, EventType.CUSTOM, epochMillis, recurrence, leadMinutesText.toLongOrNull() ?: 60L)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

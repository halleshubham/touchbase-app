package com.yourname.touchbase.ui.templates

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
import com.yourname.touchbase.data.local.MessageTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(
    onBack: () -> Unit,
    viewModel: TemplateViewModel = hiltViewModel()
) {
    val templates by viewModel.templates.collectAsState()
    var showEditor by remember { mutableStateOf<MessageTemplate?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.seedDefaultIfEmpty() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Message templates") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New template")
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(templates, key = { it.id }) { template ->
                ListItem(
                    headlineContent = { Text(template.name) },
                    supportingContent = { Text(template.bodyText, maxLines = 2) },
                    trailingContent = {
                        Row {
                            if (template.isDefault) {
                                AssistChip(onClick = {}, label = { Text("Default") })
                            } else {
                                TextButton(onClick = { viewModel.setDefault(template) }) {
                                    Text("Set default")
                                }
                            }
                            IconButton(onClick = { viewModel.delete(template) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    },
                    modifier = Modifier.clickableRow { showEditor = template }
                )
                HorizontalDivider()
            }
        }
    }

    (showEditor)?.let { template ->
        TemplateEditorDialog(
            initialName = template.name,
            initialBody = template.bodyText,
            initialDefault = template.isDefault,
            onDismiss = { showEditor = null },
            onSave = { name, body, isDefault ->
                viewModel.save(name, body, isDefault, existingId = template.id)
                showEditor = null
            }
        )
    }

    if (showCreate) {
        TemplateEditorDialog(
            initialName = "",
            initialBody = "Hi {name}, ",
            initialDefault = false,
            onDismiss = { showCreate = false },
            onSave = { name, body, isDefault ->
                viewModel.save(name, body, isDefault)
                showCreate = false
            }
        )
    }
}

@Composable
private fun TemplateEditorDialog(
    initialName: String,
    initialBody: String,
    initialDefault: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var body by remember { mutableStateOf(initialBody) }
    var isDefault by remember { mutableStateOf(initialDefault) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Template") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Template name") })
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Message (use {name} for the contact's name)") },
                    minLines = 3
                )
                Row(verticalAlignment = Alignment_CenterVertically) {
                    Checkbox(checked = isDefault, onCheckedChange = { isDefault = it })
                    Text("Make default")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank() && body.isNotBlank()) onSave(name, body, isDefault) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private val Alignment_CenterVertically = androidx.compose.ui.Alignment.CenterVertically

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))

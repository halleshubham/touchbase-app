package com.yourname.touchbase.ui.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.yourname.touchbase.data.local.ContactWithTags
import com.yourname.touchbase.data.local.MessageTemplate
import com.yourname.touchbase.data.local.Tag
import com.yourname.touchbase.util.WhatsAppLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    onOpenTemplates: () -> Unit,
    onOpenEvents: (Long, String) -> Unit,
    onOpenQueueBuilder: () -> Unit,
    onOpenSyncSettings: () -> Unit,
    viewModel: ContactListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showQuickAdd by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshFromSystemContacts() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contacts") },
                actions = {
                    TextButton(onClick = onOpenQueueBuilder) { Text("Call queue") }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Message templates") }, onClick = {
                                showMenu = false; onOpenTemplates()
                            })
                            DropdownMenuItem(text = { Text("Google sync") }, onClick = {
                                showMenu = false; onOpenSyncSettings()
                            })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showQuickAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Quick add contact")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {

            // Tag filter row
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedTagFilter == null,
                        onClick = { viewModel.setTagFilter(null) },
                        label = { Text("All") }
                    )
                }
                items(uiState.allTags, key = { it.id }) { tag ->
                    FilterChip(
                        selected = uiState.selectedTagFilter == tag.id,
                        onClick = {
                            viewModel.setTagFilter(if (uiState.selectedTagFilter == tag.id) null else tag.id)
                        },
                        label = { Text(tag.label) }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(uiState.contacts, key = { it.contact.id }) { cwt ->
                        ContactRow(
                            contactWithTags = cwt,
                            allTags = uiState.allTags,
                            templates = uiState.templates,
                            onToggleTag = { tag, assigned ->
                                viewModel.toggleTag(cwt.contact.id, tag, assigned)
                            },
                            onOpenEvents = { onOpenEvents(cwt.contact.id, cwt.contact.displayName) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showQuickAdd) {
        QuickAddDialog(
            accountLabel = uiState.quickAddAccountLabel,
            onChangeAccount = { showQuickAdd = false; onOpenSyncSettings() },
            onDismiss = { showQuickAdd = false },
            onConfirm = { name, phone ->
                viewModel.quickAdd(name, phone)
                showQuickAdd = false
            }
        )
    }
}

@Composable
private fun ContactRow(
    contactWithTags: ContactWithTags,
    allTags: List<Tag>,
    templates: List<MessageTemplate>,
    onToggleTag: (Tag, Boolean) -> Unit,
    onOpenEvents: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(contactWithTags.contact.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(contactWithTags.contact.phoneNumber, style = MaterialTheme.typography.bodySmall)
            }
            Box {
                TextButton(onClick = {
                    if (templates.size <= 1) {
                        // Single (or no) template — send immediately with default, skip the menu.
                        val body = templates.firstOrNull()?.bodyText ?: "Hi {name}!"
                        WhatsAppLauncher.openChatWithMessage(
                            context,
                            contactWithTags.contact.phoneNumber,
                            WhatsAppLauncher.renderTemplate(body, contactWithTags.contact.displayName)
                        )
                    } else {
                        showTemplatePicker = true
                    }
                }) { Text("WhatsApp") }

                DropdownMenu(expanded = showTemplatePicker, onDismissRequest = { showTemplatePicker = false }) {
                    templates.forEach { template ->
                        DropdownMenuItem(
                            text = { Text(template.name) },
                            onClick = {
                                showTemplatePicker = false
                                WhatsAppLauncher.openChatWithMessage(
                                    context,
                                    contactWithTags.contact.phoneNumber,
                                    WhatsAppLauncher.renderTemplate(template.bodyText, contactWithTags.contact.displayName)
                                )
                            }
                        )
                    }
                }
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Done" else "Tag")
            }
            TextButton(onClick = onOpenEvents) { Text("Remind") }
        }

        if (contactWithTags.tags.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                items(contactWithTags.tags, key = { it.id }) { tag ->
                    AssistChip(onClick = {}, label = { Text(tag.label) })
                }
            }
        }

        if (expanded) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                items(allTags, key = { it.id }) { tag ->
                    val assigned = contactWithTags.tags.any { it.id == tag.id }
                    FilterChip(
                        selected = assigned,
                        onClick = { onToggleTag(tag, assigned) },
                        label = { Text(tag.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAddDialog(
    accountLabel: String,
    onChangeAccount: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick add contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone number") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Saves to: $accountLabel", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onChangeAccount) { Text("Change") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank() && phone.isNotBlank()) onConfirm(name, phone) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

package com.yourname.touchbase.ui.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.yourname.touchbase.data.local.ContactWithTags
import com.yourname.touchbase.data.local.MessageTemplate
import com.yourname.touchbase.data.local.SavedFilter
import com.yourname.touchbase.data.local.Tag
import com.yourname.touchbase.util.WhatsAppLauncher
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val addedDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

private fun formatAddedDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(addedDateFormatter)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    onOpenTemplates: () -> Unit,
    onOpenEvents: (Long, String) -> Unit,
    onOpenQueueBuilder: () -> Unit,
    onOpenSyncSettings: () -> Unit,
    onOpenMergeDuplicates: () -> Unit,
    viewModel: ContactListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagingItems = viewModel.pagedContacts.collectAsLazyPagingItems()
    var showQuickAdd by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    val hasNonDefaultFilters = uiState.sortOrder != SortOrder.NEWEST_ADDED_FIRST ||
        uiState.dateFilter != DateFilter.ALL_TIME

    LaunchedEffect(Unit) { viewModel.refreshFromSystemContacts() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contacts") },
                actions = {
                    TextButton(onClick = onOpenQueueBuilder) { Text("Call queue") }
                    IconButton(onClick = { showFilterSheet = true }) {
                        BadgedBox(badge = { if (hasNonDefaultFilters) Badge() }) {
                            Icon(Icons.Filled.FilterList, contentDescription = "Sort, filter and lists")
                        }
                    }
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
                            DropdownMenuItem(text = { Text("Merge duplicate contacts") }, onClick = {
                                showMenu = false; onOpenMergeDuplicates()
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

            // Tag filter row stays visible; sort/date/lists are behind the filter icon - see dev-log/DEVELOPMENT_LOG.md (2026-09-20).
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

            // Only block on the true first load; once there's data, keep the
            // LazyColumn mounted across background refreshes so its scroll
            // position survives - see dev-log/DEVELOPMENT_LOG.md (2026-09-20).
            if (uiState.isLoading && pagingItems.itemCount == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (uiState.isLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(
                        count = pagingItems.itemCount,
                        key = pagingItems.itemKey { it.contact.id }
                    ) { index ->
                        val cwt = pagingItems[index] ?: return@items
                        ContactRow(
                            contactWithTags = cwt,
                            allTags = uiState.allTags,
                            templates = uiState.templates,
                            onToggleTag = { tag, assigned ->
                                viewModel.toggleTag(cwt.contact.id, tag, assigned)
                            },
                            onCreateTag = { label -> viewModel.createTagAndAssign(cwt.contact.id, label) },
                            onOpenEvents = { onOpenEvents(cwt.contact.id, cwt.contact.displayName) }
                        )
                        HorizontalDivider()
                    }
                    if (pagingItems.loadState.append is LoadState.Loading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        FilterSortSheet(
            uiState = uiState,
            onSetSortOrder = viewModel::setSortOrder,
            onSetDateFilter = viewModel::setDateFilter,
            onApplySavedFilter = viewModel::applySavedFilter,
            onDeleteSavedFilter = viewModel::deleteSavedFilter,
            onSaveCurrentAsList = viewModel::saveCurrentFilterAsList,
            onDismiss = { showFilterSheet = false }
        )
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterSortSheet(
    uiState: ContactListUiState,
    onSetSortOrder: (SortOrder) -> Unit,
    onSetDateFilter: (DateFilter) -> Unit,
    onApplySavedFilter: (SavedFilter) -> Unit,
    onDeleteSavedFilter: (SavedFilter) -> Unit,
    onSaveCurrentAsList: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showSaveDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Sort", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = uiState.sortOrder == SortOrder.NEWEST_ADDED_FIRST,
                    onClick = { onSetSortOrder(SortOrder.NEWEST_ADDED_FIRST) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Newest added") }
                SegmentedButton(
                    selected = uiState.sortOrder == SortOrder.OLDEST_ADDED_FIRST,
                    onClick = { onSetSortOrder(SortOrder.OLDEST_ADDED_FIRST) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Oldest added") }
            }

            Text("Added date", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = uiState.dateFilter == filter,
                        onClick = { onSetDateFilter(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("My lists", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showSaveDialog = true }) { Text("Save current as list") }
            }

            if (uiState.savedFilters.isEmpty()) {
                Text(
                    "No saved lists yet. Set a tag/date/sort combination above, then save it here to reuse later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.savedFilters.forEach { filter ->
                        InputChip(
                            selected = false,
                            onClick = { onApplySavedFilter(filter) },
                            label = { Text(filter.name) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Delete list",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onDeleteSavedFilter(filter) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveListDialog(
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                onSaveCurrentAsList(name)
                showSaveDialog = false
            }
        )
    }
}

@Composable
private fun SaveListDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save current filters as a list") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("List name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ContactRow(
    contactWithTags: ContactWithTags,
    allTags: List<Tag>,
    templates: List<MessageTemplate>,
    onToggleTag: (Tag, Boolean) -> Unit,
    onCreateTag: (String) -> Unit,
    onOpenEvents: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var showCreateTagDialog by remember { mutableStateOf(false) }
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
                Text(
                    "Added ${formatAddedDate(contactWithTags.contact.rawTimestampAdded)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                item {
                    AssistChip(onClick = { showCreateTagDialog = true }, label = { Text("+ New tag") })
                }
            }
        }
    }

    if (showCreateTagDialog) {
        CreateTagDialog(
            onDismiss = { showCreateTagDialog = false },
            onConfirm = { label ->
                onCreateTag(label)
                showCreateTagDialog = false
            }
        )
    }
}

@Composable
private fun CreateTagDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New tag") },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Tag name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (label.isNotBlank()) onConfirm(label) }) { Text("Create & apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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

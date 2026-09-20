package com.yourname.touchbase.ui.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yourname.touchbase.data.local.SavedFilter
import com.yourname.touchbase.data.local.Tag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageListsScreen(
    onManageMembers: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: ManageListsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var renameTagTarget by remember { mutableStateOf<Tag?>(null) }
    var deleteTagTarget by remember { mutableStateOf<Tag?>(null) }
    var renameFilterTarget by remember { mutableStateOf<SavedFilter?>(null) }
    var deleteFilterTarget by remember { mutableStateOf<SavedFilter?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage lists") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Text(
                    "Tags",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            if (state.tags.isEmpty()) {
                item { Text("No tags yet.", modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            items(state.tags, key = { "tag_${it.id}" }) { tag ->
                var showMenu by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(tag.label) },
                    trailingContent = {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Actions for ${tag.label}")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = {
                                    showMenu = false; renameTagTarget = tag
                                })
                                DropdownMenuItem(text = { Text("Manage members") }, onClick = {
                                    showMenu = false; onManageMembers(tag.id)
                                })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = {
                                    showMenu = false; deleteTagTarget = tag
                                })
                            }
                        }
                    }
                )
                HorizontalDivider()
            }

            item {
                Text(
                    "Saved lists (filter presets)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            if (state.savedFilters.isEmpty()) {
                item { Text("No saved lists yet.", modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            items(state.savedFilters, key = { "filter_${it.id}" }) { filter ->
                var showMenu by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(filter.name) },
                    trailingContent = {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Actions for ${filter.name}")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = {
                                    showMenu = false; renameFilterTarget = filter
                                })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = {
                                    showMenu = false; deleteFilterTarget = filter
                                })
                            }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }

    renameTagTarget?.let { tag ->
        RenameDialog(
            title = "Rename tag",
            initialValue = tag.label,
            onDismiss = { renameTagTarget = null },
            onConfirm = { newName -> viewModel.renameTag(tag.id, newName); renameTagTarget = null }
        )
    }
    deleteTagTarget?.let { tag ->
        ConfirmDeleteDialog(
            message = "Delete tag \"${tag.label}\"? This removes it from every contact it's assigned to.",
            onDismiss = { deleteTagTarget = null },
            onConfirm = { viewModel.deleteTag(tag.id); deleteTagTarget = null }
        )
    }
    renameFilterTarget?.let { filter ->
        RenameDialog(
            title = "Rename list",
            initialValue = filter.name,
            onDismiss = { renameFilterTarget = null },
            onConfirm = { newName -> viewModel.renameSavedFilter(filter, newName); renameFilterTarget = null }
        )
    }
    deleteFilterTarget?.let { filter ->
        ConfirmDeleteDialog(
            message = "Delete saved list \"${filter.name}\"?",
            onDismiss = { deleteFilterTarget = null },
            onConfirm = { viewModel.deleteSavedFilter(filter); deleteFilterTarget = null }
        )
    }
}

@Composable
private fun RenameDialog(title: String, initialValue: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ConfirmDeleteDialog(message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete?") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

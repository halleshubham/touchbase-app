package com.yourname.touchbase.ui.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateListScreen(onDone: () -> Unit, viewModel: CreateListViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var showNameDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.created) { if (state.created) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create list from contacts") },
                navigationIcon = { TextButton(onClick = onDone) { Text("Cancel") } }
            )
        },
        bottomBar = {
            Button(
                onClick = { showNameDialog = true },
                enabled = state.selectedContactIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("Create list (${state.selectedContactIds.size} selected)")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text("Search name or number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            LazyColumn(Modifier.weight(1f)) {
                items(state.results, key = { it.contact.id }) { cwt ->
                    ListItem(
                        headlineContent = { Text(cwt.contact.displayName) },
                        supportingContent = { Text(cwt.contact.phoneNumber) },
                        leadingContent = {
                            Checkbox(
                                checked = cwt.contact.id in state.selectedContactIds,
                                onCheckedChange = { viewModel.toggleContact(cwt.contact.id) }
                            )
                        }
                    )
                    HorizontalDivider()
                }
                if (state.results.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("No matching contacts.")
                        }
                    }
                }
            }
        }
    }

    if (showNameDialog) {
        NameListDialog(
            onDismiss = { showNameDialog = false },
            onConfirm = { name ->
                showNameDialog = false
                viewModel.createList(name)
            }
        )
    }
}

@Composable
private fun NameListDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this list") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("List name") }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

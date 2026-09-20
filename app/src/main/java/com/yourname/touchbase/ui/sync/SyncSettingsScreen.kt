package com.yourname.touchbase.ui.sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yourname.touchbase.sync.ContactAccount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    onBack: () -> Unit,
    viewModel: SyncSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshAccounts() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact sync") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Text(
                "New contacts you quick-add in TouchBase are saved to the account " +
                    "you pick below. If it's a Google account already signed into this " +
                    "phone, Android syncs it to your real Google Contacts the same way " +
                    "it syncs any contact you'd add from the stock Contacts app — no " +
                    "extra sign-in inside TouchBase needed.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(20.dp))
            Text("Save new contacts to:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(uiState.options, key = { it.displayLabel }) { option ->
                    val isSelected = option.displayLabel == uiState.selected.displayLabel
                    ListItem(
                        headlineContent = { Text(option.displayLabel) },
                        supportingContent = {
                            if (option is ContactAccount.Google) Text("Google account · synced by Android")
                            else Text("Stays on this device only")
                        },
                        trailingContent = { RadioButton(selected = isSelected, onClick = { viewModel.selectAccount(option) }) }
                    )
                    HorizontalDivider()
                }
            }

            if (uiState.options.none { it is ContactAccount.Google }) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "No Google account found on this device. Add one under Settings > " +
                        "Accounts with contacts sync turned on, then come back here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = { viewModel.triggerManualSync() }, modifier = Modifier.fillMaxWidth()) {
                Text("Sync Google account now")
            }
            if (uiState.syncQueued) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sync requested — Android will run it in the background shortly.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

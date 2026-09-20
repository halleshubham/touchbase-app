package com.yourname.touchbase.ui.callqueue

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourname.touchbase.data.local.ContactWithTags
import com.yourname.touchbase.data.local.Tag
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBuilderScreen(
    onSessionReady: (Long) -> Unit,
    onBack: () -> Unit,
    queueBuilderViewModel: QueueBuilderViewModel = hiltViewModel(),
    callQueueViewModel: CallQueueViewModel = hiltViewModel()
) {
    val uiState by queueBuilderViewModel.uiState.collectAsState()
    var selectedTag by remember { mutableStateOf<Tag?>(null) }
    val scope = rememberCoroutineScope()

    val filtered: List<ContactWithTags> = if (selectedTag == null) uiState.contacts
    else uiState.contacts.filter { cwt -> cwt.tags.any { it.id == selectedTag!!.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Build call queue") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    scope.launch {
                        val ids = filtered.map { it.contact.id }
                        val description = selectedTag?.let { "Tag: ${it.label}" } ?: "All contacts"
                        val sessionId = callQueueViewModel.startSession(ids, description)
                        onSessionReady(sessionId)
                    }
                },
                enabled = filtered.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("Start queue (${filtered.size} contacts)")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = selectedTag == null, onClick = { selectedTag = null }, label = { Text("All") })
                uiState.allTags.forEach { tag ->
                    FilterChip(
                        selected = selectedTag?.id == tag.id,
                        onClick = { selectedTag = tag },
                        label = { Text(tag.label) }
                    )
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { it.contact.id }) { cwt ->
                    ListItem(
                        headlineContent = { Text(cwt.contact.displayName) },
                        supportingContent = { Text(cwt.contact.phoneNumber) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

package com.yourname.touchbase.ui.callqueue

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yourname.touchbase.call.CallStateWatcher
import com.yourname.touchbase.data.local.CallFeedback
import com.yourname.touchbase.data.local.CallOutcome
import com.yourname.touchbase.data.local.QueueRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallQueueScreen(
    onFinished: () -> Unit,
    viewModel: CallQueueViewModel = hiltViewModel()
) {
    val queue by viewModel.queue.collectAsState()
    val context = LocalContext.current
    var feedbackTarget by remember { mutableStateOf<QueueRow?>(null) }

    val pending = queue.filter { it.item.outcome == CallOutcome.PENDING }
    val current = pending.firstOrNull()
    val totalCount = queue.size
    val doneCount = totalCount - pending.size

    // Watches for the call-ended transition so we can bring the app back
    // to the foreground and prompt for feedback without the user tapping
    // "back" from the dialer themselves.
    val watcher = remember(current?.item?.id) {
        CallStateWatcher {
            current?.let {
                viewModel.markCalled(it.item)
                feedbackTarget = it
            }
        }
    }
    DisposableEffect(current?.item?.id) {
        watcher.register(context)
        onDispose { watcher.unregister(context) }
    }

    LaunchedEffect(pending.size) {
        if (totalCount > 0 && pending.isEmpty()) {
            viewModel.completeSessionIfDone()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Call queue") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = { if (totalCount == 0) 0f else doneCount / totalCount.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text("$doneCount of $totalCount called")
            Spacer(Modifier.height(32.dp))

            if (current == null) {
                Text("Queue complete 🎉", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onFinished) { Text("Back to contacts") }
            } else {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(current.contact.displayName, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(current.contact.phoneNumber, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = { viewModel.markSkipped(current.item) }) {
                        Text("Skip")
                    }
                    Button(onClick = {
                        // User explicitly taps Call — a deliberate action per call,
                        // never a silent/background dial. The CallStateWatcher above
                        // picks up when this call ends and prompts for feedback.
                        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${current.contact.phoneNumber}"))
                        context.startActivity(intent)
                    }) {
                        Text("Call")
                    }
                }
            }
        }
    }

    feedbackTarget?.let { row ->
        FeedbackSheet(
            contactName = row.contact.displayName,
            onDismiss = { feedbackTarget = null },
            onSubmit = { feedback, note ->
                viewModel.recordFeedback(row.item, feedback, note)
                feedbackTarget = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackSheet(
    contactName: String,
    onDismiss: () -> Unit,
    onSubmit: (CallFeedback, String?) -> Unit
) {
    var note by remember { mutableStateOf("") }
    var showNoteField by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp)) {
            Text("How did the call with $contactName go?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quickFeedbackOptions) { (feedback, label) ->
                    AssistChip(
                        onClick = { onSubmit(feedback, note.ifBlank { null }) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showNoteField = !showNoteField }) {
                Text(if (showNoteField) "Hide note" else "Add note")
            }
            if (showNoteField) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private val quickFeedbackOptions = listOf(
    CallFeedback.ANSWERED to "Answered",
    CallFeedback.VOICEMAIL to "Voicemail",
    CallFeedback.NO_ANSWER to "No answer",
    CallFeedback.NOT_INTERESTED to "Not interested",
    CallFeedback.CALLBACK_LATER to "Call back later",
    CallFeedback.WRONG_NUMBER to "Wrong number"
)

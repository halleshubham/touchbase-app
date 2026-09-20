package com.yourname.touchbase.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SessionStatus { IN_PROGRESS, COMPLETED, ABANDONED }
enum class CallOutcome { PENDING, CALLED, SKIPPED, FAILED }
enum class CallFeedback { NONE, ANSWERED, VOICEMAIL, NOT_INTERESTED, CALLBACK_LATER, WRONG_NUMBER, NO_ANSWER }

@Entity(tableName = "call_sessions")
data class CallSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val filterDescription: String, // human-readable summary of the filter used, e.g. "Tag: Clients"
    val status: SessionStatus = SessionStatus.IN_PROGRESS
)

@Entity(tableName = "call_session_items")
data class CallSessionItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val contactId: Long,
    val orderIndex: Int,
    val outcome: CallOutcome = CallOutcome.PENDING,
    val feedback: CallFeedback = CallFeedback.NONE,
    val feedbackNote: String? = null,
    val calledAtEpochMillis: Long? = null,
    val callDurationSeconds: Long? = null
)

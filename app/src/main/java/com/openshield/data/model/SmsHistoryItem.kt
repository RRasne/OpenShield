package com.openshield.data.model

data class SmsHistoryItem(
    val id: Long,
    val sender: String,
    val body: String,
    val receivedAt: Long
)

enum class FeedbackVerdict {
    SPAM,
    SUSPICIOUS,
    NOT_SPAM
}

data class CommunityContributionSummary(
    val spamCount: Int = 0,
    val suspiciousCount: Int = 0,
    val notSpamCount: Int = 0,
    val lastSyncAt: Long? = null
)

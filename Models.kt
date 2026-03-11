package com.openshield.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ─── SMS Mesaj Modeli ──────────────────────────────────────────────────────────

data class SmsMessage(
    val sender: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis()
)

// ─── Spam Analiz Sonucu ────────────────────────────────────────────────────────

data class SpamResult(
    val classification: Classification,
    val score: Float,           // 0.0 - 1.0
    val numberScore: Float,
    val contentScore: Float,
    val mlScore: Float,
    val triggeredRules: List<String>
)

enum class Classification {
    SPAM,
    SUSPICIOUS,
    CLEAN
}

// ─── Room Entity: Kullanıcı Kara/Beyaz Liste ──────────────────────────────────

@Entity(tableName = "user_filter_list")
data class UserFilterEntry(
    @PrimaryKey val numberHash: String,  // SHA-256 hash, düz metin yok!
    val listType: ListType,
    val addedAt: Long = System.currentTimeMillis(),
    val note: String? = null
)

enum class ListType { BLACKLIST, WHITELIST }

// ─── Room Entity: Topluluk Spam Numaraları ────────────────────────────────────

@Entity(tableName = "community_spam_numbers")
data class CommunitySpamNumber(
    @PrimaryKey val numberHash: String,  // SHA-256 hash
    val reportCount: Int = 1,
    val lastReportedAt: Long = System.currentTimeMillis(),
    val category: SpamCategory = SpamCategory.UNKNOWN
)

enum class SpamCategory {
    UNKNOWN,
    PHISHING,
    PROMOTION,
    FRAUD,
    ROBOCALL,
    DEBT_COLLECTOR
}

// ─── Room Entity: Engellenen SMS Geçmişi ─────────────────────────────────────

@Entity(tableName = "blocked_sms_log")
data class BlockedSmsLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderHash: String,      // Numaranın hash'i
    val spamScore: Float,
    val classification: Classification,
    val triggeredRules: String,  // JSON array
    val blockedAt: Long = System.currentTimeMillis()
    // NOT: SMS içeriği saklanmaz — gizlilik!
)

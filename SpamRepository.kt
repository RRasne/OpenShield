package com.openshield.data.repository

import com.openshield.data.db.*
import kotlinx.coroutines.flow.Flow

class SpamRepository(private val db: SpamDatabase) {

    // ─── Spam Numaraları ──────────────────────────────────────────────────────

    val allSpamNumbers: Flow<List<SpamNumberEntity>> = db.spamNumberDao().getAllFlow()

    suspend fun isSpam(number: String): Boolean =
        db.spamNumberDao().findByNumber(cleanNumber(number)) != null

    suspend fun addSpam(number: String, label: String = "") {
        db.spamNumberDao().insert(SpamNumberEntity(number = cleanNumber(number), label = label))
    }

    suspend fun removeSpam(number: String) =
        db.spamNumberDao().deleteByNumber(cleanNumber(number))

    suspend fun spamCount(): Int = db.spamNumberDao().count()

    // ─── Beyaz Liste ──────────────────────────────────────────────────────────

    val allWhitelist: Flow<List<WhitelistEntity>> = db.whitelistDao().getAllFlow()

    suspend fun isWhitelisted(number: String): Boolean =
        db.whitelistDao().findByNumber(cleanNumber(number)) != null

    suspend fun addWhitelist(number: String, name: String = "") {
        db.whitelistDao().insert(WhitelistEntity(number = cleanNumber(number), name = name))
    }

    suspend fun removeWhitelist(number: String) =
        db.whitelistDao().deleteByNumber(cleanNumber(number))

    // ─── Engelleme Geçmişi ────────────────────────────────────────────────────

    val recentBlocked: Flow<List<BlockedLogEntity>> = db.blockLogDao().getRecentFlow()

    suspend fun logBlocked(sender: String, reason: String, score: Float) {
        db.blockLogDao().insert(BlockedLogEntity(sender = sender, reason = reason, score = score))
    }

    suspend fun totalBlocked(): Int = db.blockLogDao().totalCount()

    suspend fun clearHistory() = db.blockLogDao().clearAll()

    // ─── Bekleyen İncelemeler ─────────────────────────────────────────────────

    suspend fun addPendingReview(sender: String, reason: String, score: Float) {
        db.pendingReviewDao().insert(
            PendingReviewEntity(sender = sender, reason = reason, score = score)
        )
    }

    suspend fun getPendingReviews(): List<PendingReviewEntity> =
        db.pendingReviewDao().getAll()

    suspend fun pendingReviewCount(): Int = db.pendingReviewDao().count()

    /**
     * Kullanıcı kararı:
     * isSpam = true  → kara listeye ekle + log'a yaz
     * isSpam = false → yok say, sil
     */
    suspend fun resolvePendingReview(entity: PendingReviewEntity, isSpam: Boolean) {
        if (isSpam) {
            addSpam(entity.sender, label = "Şüpheli onaylandı")
            logBlocked(sender = entity.sender, reason = entity.reason, score = entity.score)
        }
        db.pendingReviewDao().deleteById(entity.id)
    }

    // ─── Yardımcı ─────────────────────────────────────────────────────────────

    private fun cleanNumber(number: String) =
        number.trim().replace(" ", "").replace("-", "")
}

package com.openshield.data.repository

import com.openshield.data.db.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpamRepository @Inject constructor(private val db: SpamDatabase) {

    // ─── Spam Numaraları ──────────────────────────────────────────────────────

    // MainViewModel'in beklediği isim
    val userSpamNumbers: Flow<List<SpamNumberEntity>> = db.spamNumberDao().getAllFlow()

    // Geriye dönük uyumluluk için alias
    val allSpamNumbers: Flow<List<SpamNumberEntity>> = userSpamNumbers

    suspend fun isSpam(number: String): Boolean {
        val clean = cleanNumber(number)
        return db.spamNumberDao().findByNumber(clean) != null
    }

    suspend fun addSpam(number: String, label: String = "") {
        db.spamNumberDao().insert(
            SpamNumberEntity(number = cleanNumber(number), label = label)
        )
    }

    suspend fun removeSpam(number: String) {
        db.spamNumberDao().deleteByNumber(cleanNumber(number))
    }

    suspend fun spamCount(): Int = db.spamNumberDao().count()

    // ─── Beyaz Liste ──────────────────────────────────────────────────────────

    val allWhitelist: Flow<List<WhitelistEntity>> = db.whitelistDao().getAllFlow()

    suspend fun isWhitelisted(number: String): Boolean {
        return db.whitelistDao().findByNumber(cleanNumber(number)) != null
    }

    suspend fun addWhitelist(number: String, name: String = "") {
        db.whitelistDao().insert(
            WhitelistEntity(number = cleanNumber(number), name = name)
        )
    }

    suspend fun removeWhitelist(number: String) {
        db.whitelistDao().deleteByNumber(cleanNumber(number))
    }

    // ─── Engelleme Geçmişi ────────────────────────────────────────────────────

    val recentBlocked: Flow<List<BlockedLogEntity>> = db.blockLogDao().getRecentFlow()

    suspend fun logBlocked(sender: String, reason: String, score: Float) {
        db.blockLogDao().insert(
            BlockedLogEntity(sender = sender, reason = reason, score = score)
        )
    }

    suspend fun totalBlocked(): Int = db.blockLogDao().totalCount()

    suspend fun clearHistory() = db.blockLogDao().clearAll()

    // ─── Yardımcı ─────────────────────────────────────────────────────────────

    private fun cleanNumber(number: String): String {
        return number.trim().replace(" ", "").replace("-", "")
    }
}
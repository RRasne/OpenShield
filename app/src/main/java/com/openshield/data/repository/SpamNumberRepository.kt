package com.openshield.data.repository

import com.openshield.data.db.BlockedLogEntity
import com.openshield.data.db.SpamDatabase
import com.openshield.data.db.SpamNumberEntity
import com.openshield.data.db.WhitelistEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpamNumberRepository @Inject constructor(
    private val db: SpamDatabase
) {
    suspend fun isInBlacklist(number: String): Boolean =
        db.spamNumberDao().findByNumber(normalize(number)) != null

    suspend fun isInWhitelist(number: String): Boolean =
        db.whitelistDao().findByNumber(normalize(number)) != null

    suspend fun getCommunityReportCount(number: String): Int =
        db.spamNumberDao().findByNumber(normalize(number))?.reportCount ?: 0

    val allSpamNumbers: Flow<List<SpamNumberEntity>> = db.spamNumberDao().getAllFlow()
    val allWhitelist: Flow<List<WhitelistEntity>> = db.whitelistDao().getAllFlow()
    val recentBlocked: Flow<List<BlockedLogEntity>> = db.blockLogDao().getRecentFlow()

    suspend fun addToBlacklist(number: String, label: String = "") {
        db.spamNumberDao().insert(
            SpamNumberEntity(number = normalize(number), label = label, isUserAdded = true, reportCount = 1)
        )
    }

    suspend fun removeFromBlacklist(number: String) =
        db.spamNumberDao().deleteByNumber(normalize(number))

    suspend fun addToWhitelist(number: String, name: String = "") {
        db.whitelistDao().insert(WhitelistEntity(number = normalize(number), name = name))
    }

    suspend fun removeFromWhitelist(number: String) =
        db.whitelistDao().deleteByNumber(normalize(number))

    suspend fun logBlocked(sender: String, reason: String, score: Float) {
        db.blockLogDao().insert(
            BlockedLogEntity(sender = normalize(sender), reason = reason, score = score)
        )
    }

    suspend fun clearHistory() = db.blockLogDao().clearAll()

    suspend fun getStats(): SpamListStats = SpamListStats(
        bundledCount = db.spamNumberDao().bundledCount(),
        userCount = db.spamNumberDao().userCount(),
        whitelistCount = db.whitelistDao().count()
    )

    private fun normalize(number: String) =
        number.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
}

data class SpamListStats(
    val bundledCount: Int,
    val userCount: Int,
    val whitelistCount: Int
)

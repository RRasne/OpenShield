package com.openshield.data.repository

import com.openshield.data.db.BlockedLogEntity
import com.openshield.data.db.CommunityReportEntity
import com.openshield.data.db.PendingReviewEntity
import com.openshield.data.db.SpamDatabase
import com.openshield.data.db.SpamNumberEntity
import com.openshield.data.db.WhitelistEntity
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpamNumberRepository @Inject constructor(
    private val db: SpamDatabase
) {
    companion object {
        const val COMMUNITY_THRESHOLD = 5
    }

    suspend fun isInBlacklist(number: String): Boolean =
        db.spamNumberDao().findByNumber(cleanNumber(number)) != null

    suspend fun isInWhitelist(number: String): Boolean =
        db.whitelistDao().findByNumber(cleanNumber(number)) != null

    suspend fun getCommunityReportCount(number: String): Int {
        val hash = hashNumber(number)
        return db.communityReportDao().getReportCount(hash)
            ?: db.spamNumberDao().findByNumber(cleanNumber(number))?.reportCount
            ?: 0
    }

    val spamNumbers: Flow<List<SpamNumberEntity>>
        get() = db.spamNumberDao().getUserAddedFlow()

    val allSpamNumbers: Flow<List<SpamNumberEntity>>
        get() = db.spamNumberDao().getAllFlow()

    val whitelist: Flow<List<WhitelistEntity>>
        get() = db.whitelistDao().getAllFlow()

    val allWhitelist: Flow<List<WhitelistEntity>>
        get() = whitelist

    val blockedLog: Flow<List<BlockedLogEntity>>
        get() = db.blockLogDao().getRecentFlow()

    val recentBlocked: Flow<List<BlockedLogEntity>>
        get() = blockedLog

    val pendingReviews: Flow<List<PendingReviewEntity>>
        get() = db.pendingReviewDao().getAllFlow()

    val communityReports: Flow<List<CommunityReportEntity>>
        get() = db.communityReportDao().getAllFlow()

    suspend fun addSpam(number: String, label: String = "") {
        db.spamNumberDao().insert(
            SpamNumberEntity(number = cleanNumber(number), label = label, isUserAdded = true)
        )
    }

    suspend fun addToBlacklist(number: String, label: String = "") = addSpam(number, label)

    suspend fun removeSpam(number: String) =
        db.spamNumberDao().deleteByNumber(cleanNumber(number))

    suspend fun removeFromBlacklist(number: String) = removeSpam(number)

    suspend fun addWhitelist(number: String, name: String = "") {
        db.whitelistDao().insert(WhitelistEntity(number = cleanNumber(number), name = name))
    }

    suspend fun addToWhitelist(number: String, name: String = "") = addWhitelist(number, name)

    suspend fun removeWhitelist(number: String) =
        db.whitelistDao().deleteByNumber(cleanNumber(number))

    suspend fun removeFromWhitelist(number: String) = removeWhitelist(number)

    suspend fun logBlocked(sender: String, reason: String, score: Float) {
        db.blockLogDao().insert(
            BlockedLogEntity(sender = cleanNumber(sender), reason = reason, score = score)
        )
    }

    suspend fun logSuspicious(sender: String, reason: String, score: Float) {
        db.pendingReviewDao().insert(
            PendingReviewEntity(sender = cleanNumber(sender), reason = reason, score = score)
        )
    }

    suspend fun decideSuspicious(item: PendingReviewEntity, isSpam: Boolean) {
        if (isSpam) {
            db.blockLogDao().insert(
                BlockedLogEntity(
                    sender = item.sender,
                    reason = item.reason,
                    score = item.score,
                    blockedAt = item.receivedAt
                )
            )
            db.communityReportDao().addReport(hashNumber(item.sender))
        }
        db.pendingReviewDao().deleteById(item.id)
    }

    suspend fun reportAsCommunitySpam(number: String) {
        db.communityReportDao().addReport(hashNumber(number))
    }

    suspend fun clearHistory() = db.blockLogDao().clearAll()

    private fun cleanNumber(number: String): String =
        number.trim().replace(Regex("[\\s\\-()]"), "")

    fun hashNumber(number: String): String {
        val normalized = number
            .replace(Regex("[^0-9]"), "")
            .trimStart('0')
            .let { if (it.startsWith("90") && it.length == 12) it.substring(2) else it }
        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

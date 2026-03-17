package com.openshield.data.repository

import android.content.Context
import com.openshield.data.BundledSpamImporter
import com.openshield.data.SpamReporter
import com.openshield.data.db.BlockedLogEntity
import com.openshield.data.db.PendingReviewEntity
import com.openshield.data.db.SpamDatabase
import com.openshield.data.db.SpamNumberEntity
import com.openshield.data.db.WhitelistEntity
import com.openshield.worker.CommunityReportWorker
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpamRepository @Inject constructor(
    private val db: SpamDatabase,
    private val appContext: Context
) {
    val userSpamNumbers: Flow<List<SpamNumberEntity>> = db.spamNumberDao().getUserAddedFlow()
    val allWhitelist: Flow<List<WhitelistEntity>> = db.whitelistDao().getAllFlow()
    val recentBlocked: Flow<List<BlockedLogEntity>> = db.blockLogDao().getRecentFlow()
    val pendingReviews: Flow<List<PendingReviewEntity>> = db.pendingReviewDao().getAllFlow()

    suspend fun isSpam(number: String): Boolean {
        val normalized = cleanNumber(number)
        return db.spamNumberDao().findByNumber(normalized) != null
    }

    suspend fun isInBlacklist(number: String): Boolean = isSpam(number)

    suspend fun isWhitelisted(number: String): Boolean {
        return db.whitelistDao().findByNumber(cleanNumber(number)) != null
    }

    suspend fun isInWhitelist(number: String): Boolean = isWhitelisted(number)

    suspend fun addSpam(number: String, label: String = "") {
        db.spamNumberDao().insert(
            SpamNumberEntity(number = cleanNumber(number), label = label)
        )
    }

    suspend fun addWhitelist(number: String, name: String = "") {
        db.whitelistDao().insert(
            WhitelistEntity(number = cleanNumber(number), name = name)
        )
    }

    suspend fun logBlocked(sender: String, reason: String, score: Float) {
        db.blockLogDao().insert(
            BlockedLogEntity(sender = sender, reason = reason, score = score)
        )
    }

    suspend fun logSuspicious(sender: String, reason: String, score: Float) {
        db.pendingReviewDao().insert(
            PendingReviewEntity(
                sender = cleanNumber(sender),
                reason = reason,
                score = score
            )
        )
    }

    suspend fun syncCommunityList(): Boolean {
        val csv = SpamReporter().fetchCommunityCsv() ?: return false
        BundledSpamImporter.importFromRemoteCsv(appContext, csv)
        CommunityReportWorker.setLastSyncAt(appContext, System.currentTimeMillis())
        return true
    }

    private fun cleanNumber(number: String): String {
        return number.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
    }
}

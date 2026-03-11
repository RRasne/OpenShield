package com.openshield.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.openshield.data.BundledSpamImporter
import com.openshield.data.SpamReporter
import com.openshield.data.db.BlockedLogEntity
import com.openshield.data.db.SmsFeedbackEntity
import com.openshield.data.db.SpamDatabase
import com.openshield.data.db.SpamNumberEntity
import com.openshield.data.db.WhitelistEntity
import com.openshield.data.model.CommunityContributionSummary
import com.openshield.data.model.FeedbackVerdict
import com.openshield.data.model.SmsHistoryItem
import com.openshield.worker.CommunityReportWorker
import com.openshield.worker.SpamReportUploadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpamRepository @Inject constructor(
    private val db: SpamDatabase,
    private val appContext: Context
) {

    val userSpamNumbers: Flow<List<SpamNumberEntity>> = db.spamNumberDao().getAllFlow()
    val allSpamNumbers: Flow<List<SpamNumberEntity>> = userSpamNumbers
    val allWhitelist: Flow<List<WhitelistEntity>> = db.whitelistDao().getAllFlow()
    val recentBlocked: Flow<List<BlockedLogEntity>> = db.blockLogDao().getRecentFlow()

    suspend fun isSpam(number: String): Boolean {
        val normalized = cleanNumber(number)
        val hashed = SpamReporter.hashPhoneNumber(normalized)
        return db.spamNumberDao().findByNumber(normalized) != null ||
            db.spamNumberDao().findByNumber(hashed) != null
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

    suspend fun logBlocked(sender: String, reason: String, score: Float) {
        db.blockLogDao().insert(
            BlockedLogEntity(sender = sender, reason = reason, score = score)
        )
    }

    suspend fun totalBlocked(): Int = db.blockLogDao().totalCount()

    suspend fun clearHistory() {
        db.blockLogDao().clearAll()
        db.smsFeedbackDao().clearAll()
    }

    suspend fun clearFeedback() {
        db.smsFeedbackDao().clearAll()
    }

    fun userSmsFeedbackFlow(): Flow<List<SmsFeedbackEntity>> = db.smsFeedbackDao().getAllFlow()

    suspend fun loadSmsHistory(limit: Int = 400): List<SmsHistoryItem> {
        if (!hasReadSmsPermission()) return emptyList()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        val result = mutableListOf<SmsHistoryItem>()
        val cursor = appContext.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )

        cursor?.use {
            val idIdx = it.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (it.moveToNext() && result.size < limit) {
                result.add(
                    SmsHistoryItem(
                        id = it.getLong(idIdx),
                        sender = it.getString(addressIdx).orEmpty(),
                        body = it.getString(bodyIdx).orEmpty(),
                        receivedAt = it.getLong(dateIdx)
                    )
                )
            }
        }

        return result
    }

    suspend fun markSmsAndQueueCommunityReport(
        messageId: Long,
        sender: String,
        verdict: FeedbackVerdict
    ) {
        val normalized = cleanNumber(sender)
        if (normalized.isBlank()) return

        val numberHash = SpamReporter.hashPhoneNumber(normalized)
        db.smsFeedbackDao().upsert(
            SmsFeedbackEntity(
                messageId = messageId,
                numberHash = numberHash,
                sender = normalized,
                verdict = verdict.name,
                isSpam = verdict == FeedbackVerdict.SPAM
            )
        )

        if (verdict == FeedbackVerdict.SPAM) {
            addSpam(normalized, "Kullanici spam olarak isaretledi")
        }

        SpamReportUploadWorker.enqueue(
            context = appContext,
            numberHash = numberHash,
            rules = listOf("USER_MARK_${verdict.name}"),
            score = scoreFor(verdict),
            category = categoryFor(verdict)
        )

        CommunityReportWorker.enqueueSyncIfNeeded(appContext)
    }

    suspend fun getCommunityContributionSummary(): CommunityContributionSummary {
        val rows = db.smsFeedbackDao().getAllFlow().first()
        return CommunityContributionSummary(
            spamCount = rows.count { it.verdict == FeedbackVerdict.SPAM.name },
            suspiciousCount = rows.count { it.verdict == FeedbackVerdict.SUSPICIOUS.name },
            notSpamCount = rows.count { it.verdict == FeedbackVerdict.NOT_SPAM.name },
            lastSyncAt = CommunityReportWorker.getLastSyncAt(appContext)
        )
    }

    suspend fun syncCommunityList(): Boolean {
        val csv = SpamReporter().fetchCommunityCsv() ?: return false
        BundledSpamImporter.importFromRemoteCsv(appContext, csv)
        CommunityReportWorker.setLastSyncAt(appContext, System.currentTimeMillis())
        return true
    }

    private fun hasReadSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun scoreFor(verdict: FeedbackVerdict): Float {
        return when (verdict) {
            FeedbackVerdict.SPAM -> 1f
            FeedbackVerdict.SUSPICIOUS -> 0.5f
            FeedbackVerdict.NOT_SPAM -> 0f
        }
    }

    private fun categoryFor(verdict: FeedbackVerdict): String {
        return when (verdict) {
            FeedbackVerdict.SPAM -> "USER_REPORTED_SPAM"
            FeedbackVerdict.SUSPICIOUS -> "USER_REPORTED_SUSPICIOUS"
            FeedbackVerdict.NOT_SPAM -> "USER_REPORTED_NOT_SPAM"
        }
    }

    private fun cleanNumber(number: String): String {
        return number.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
    }
}



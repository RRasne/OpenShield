package com.openshield.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.openshield.data.ReportResult
import com.openshield.data.SpamReporter

class SpamReportUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!CommunityReportWorker.hasConsent(applicationContext)) return Result.success()

        val numberHash = inputData.getString(KEY_NUMBER_HASH) ?: return Result.failure()
        val score = inputData.getFloat(KEY_SCORE, -1f)
        if (score !in 0f..1f) return Result.failure()

        val rules = inputData.getStringArray(KEY_RULES)?.toList().orEmpty().ifEmpty { listOf("UNKNOWN_RULE") }
        val category = inputData.getString(KEY_CATEGORY).orEmpty().ifBlank { "UNKNOWN" }

        val result = SpamReporter().reportHashed(
            numberHash = numberHash,
            triggeredRules = rules,
            score = score,
            category = category
        )

        return when (result) {
            is ReportResult.Success -> Result.success()
            is ReportResult.Failed -> Result.retry()
        }
    }

    companion object {
        private const val KEY_NUMBER_HASH = "number_hash"
        private const val KEY_RULES = "rules"
        private const val KEY_SCORE = "score"
        private const val KEY_CATEGORY = "category"

        fun enqueue(
            context: Context,
            numberHash: String,
            rules: List<String>,
            score: Float,
            category: String
        ) {
            if (!CommunityReportWorker.hasConsent(context)) return

            val data = Data.Builder()
                .putString(KEY_NUMBER_HASH, numberHash)
                .putStringArray(KEY_RULES, rules.filter { it.isNotBlank() }.toTypedArray())
                .putFloat(KEY_SCORE, score.coerceIn(0f, 1f))
                .putString(KEY_CATEGORY, category)
                .build()

            val request = OneTimeWorkRequestBuilder<SpamReportUploadWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}

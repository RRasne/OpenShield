package com.openshield.worker

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class CommunityReportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Internet izni olmadığı için şimdilik no-op
        return Result.success()
    }

    companion object {
        private const val PREF_FILE = "openshield"
        private const val KEY_CONSENT = "community_consent"
        private const val WORK_NAME = "community_report"

        fun setConsent(context: Context, accepted: Boolean) {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_CONSENT, accepted).apply()
        }

        fun hasConsent(context: Context): Boolean {
            return context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .getBoolean(KEY_CONSENT, false)
        }

        fun schedule(context: Context) {
            if (!hasConsent(context)) return

            val request = PeriodicWorkRequestBuilder<CommunityReportWorker>(
                24, TimeUnit.HOURS
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
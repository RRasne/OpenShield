package com.openshield.worker

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class CommunityReportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Community uploads are handled by SpamReportUploadWorker (event-driven queue).
        return Result.success()
    }

    companion object {
        private const val PREF_FILE = "openshield"
        private const val KEY_CONSENT = "community_consent"
        private const val KEY_DATA_SHARING = "data_sharing"
        private const val WORK_NAME = "community_report"

        fun setConsent(context: Context, accepted: Boolean) {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CONSENT, accepted)
                .putBoolean(KEY_DATA_SHARING, accepted)
                .apply()

            if (accepted) schedule(context) else cancel(context)
        }

        fun hasConsent(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_CONSENT, prefs.getBoolean(KEY_DATA_SHARING, false))
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

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

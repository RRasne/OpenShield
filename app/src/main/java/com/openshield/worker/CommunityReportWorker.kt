package com.openshield.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.openshield.data.repository.SpamRepository
import com.openshield.data.db.SpamDatabase
import java.util.concurrent.TimeUnit

class CommunityReportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!hasConsent(applicationContext)) return Result.success()

        val repository = SpamRepository(
            db = SpamDatabase.getInstance(applicationContext),
            appContext = applicationContext
        )

        return if (repository.syncCommunityList()) Result.success() else Result.retry()
    }

    companion object {
        private const val PREF_FILE = "openshield"
        private const val KEY_CONSENT = "community_consent"
        private const val KEY_DATA_SHARING = "data_sharing"
        private const val KEY_LAST_SYNC_AT = "community_last_sync_at"
        private const val PERIODIC_WORK_NAME = "community_report"
        private const val ONE_TIME_SYNC_WORK_NAME = "community_sync_on_change"

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

        fun getLastSyncAt(context: Context): Long? {
            val value = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_SYNC_AT, 0L)
            return value.takeIf { it > 0L }
        }

        fun setLastSyncAt(context: Context, timestamp: Long) {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SYNC_AT, timestamp)
                .apply()
        }

        fun schedule(context: Context) {
            if (!hasConsent(context)) return

            val request = PeriodicWorkRequestBuilder<CommunityReportWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun enqueueSyncIfNeeded(context: Context) {
            if (!hasConsent(context)) return

            val request = OneTimeWorkRequestBuilder<CommunityReportWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(ONE_TIME_SYNC_WORK_NAME)
        }
    }
}

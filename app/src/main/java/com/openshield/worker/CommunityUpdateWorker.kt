package com.openshield.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.openshield.data.db.SpamDatabase
import com.openshield.data.repository.SpamRepository
import com.openshield.util.ConsentManager
import java.util.concurrent.TimeUnit

class CommunityUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "community_sync_once"

        fun runOnce(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()

            val request = OneTimeWorkRequestBuilder<CommunityUpdateWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val consentManager = ConsentManager(applicationContext)
        if (!consentManager.isCommunityConsentGiven) return Result.success()

        val repository = SpamRepository(
            db = SpamDatabase.getInstance(applicationContext),
            appContext = applicationContext
        )

        return if (repository.syncCommunityList()) {
            consentManager.updateLastSync()
            Result.success()
        } else {
            Result.retry()
        }
    }
}

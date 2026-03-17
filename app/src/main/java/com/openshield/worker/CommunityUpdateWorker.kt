package com.openshield.worker

import android.content.Context
import androidx.work.*
import com.openshield.data.db.CommunityReportEntity
import com.openshield.data.db.SpamDatabase
import com.openshield.util.ConsentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class CommunityUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME    = "community_sync_once"
        private const val API_BASE = "https://openshield-api.rasne.workers.dev"

        private const val TASK_SYNC   = "sync"
        private const val TASK_REPORT = "report"
        const val KEY_TASK        = "task"
        const val KEY_NUMBER_HASH = "numberHash"
        const val KEY_TOKENS      = "tokens"
        const val KEY_RULES       = "rules"

        /** Wi-Fi bağlanınca WifiSyncManager çağırır */
        fun runSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()

            val request = OneTimeWorkRequestBuilder<CommunityUpdateWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_TASK to TASK_SYNC))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME, ExistingWorkPolicy.KEEP, request
            )
        }

        /** Kullanıcı spam bildirdi — token'larla birlikte gönder */
        fun reportSpam(
            context: Context,
            numberHash: String,
            tokens: List<String>,
            triggeredRules: List<String>
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()

            val request = OneTimeWorkRequestBuilder<CommunityUpdateWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(
                    KEY_TASK        to TASK_REPORT,
                    KEY_NUMBER_HASH to numberHash,
                    KEY_TOKENS      to JSONArray(tokens).toString(),
                    KEY_RULES       to JSONArray(triggeredRules).toString()
                ))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }

        fun hasConsent(context: Context): Boolean =
            ConsentManager(context).isCommunityConsentGiven

        /** Onboarding veya Ayarlar'dan consent değiştir */
        fun setConsent(context: Context, enabled: Boolean) {
            ConsentManager(context).isCommunityConsentGiven = enabled
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val consentManager = ConsentManager(applicationContext)
        if (!consentManager.isCommunityConsentGiven) {
            return@withContext Result.success()
        }

        return@withContext when (inputData.getString(KEY_TASK)) {
            TASK_REPORT -> doReport()
            else        -> doSync(consentManager)
        }
    }

    // ─── Spam Bildir ──────────────────────────────────────────────────────────

    private suspend fun doReport(): Result {
        val numberHash = inputData.getString(KEY_NUMBER_HASH) ?: return Result.failure()
        if (numberHash.length != 64) return Result.failure()

        val tokens = parseJsonArray(inputData.getString(KEY_TOKENS))
        val rules  = parseJsonArray(inputData.getString(KEY_RULES))

        val body = JSONObject().apply {
            put("numberHash", numberHash)
            put("tokens", JSONArray(tokens))
            put("triggeredRules", JSONArray(rules))
        }

        return try {
            val conn = (URL("$API_BASE/report").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "OpenShield-Android/1.0")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val ok = conn.responseCode in 200..299
            conn.disconnect()
            if (ok) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    // ─── Liste Güncelle ───────────────────────────────────────────────────────

    private suspend fun doSync(consentManager: ConsentManager): Result {
        return try {
            val lastSync = consentManager.lastSyncTimestamp
            val conn = (URL("$API_BASE/community-list?since=$lastSync")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "OpenShield-Android/1.0")
            }

            if (conn.responseCode != 200) {
                conn.disconnect()
                return Result.retry()
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val jsonArray = JSONArray(body)
            val dao = SpamDatabase.getInstance(applicationContext).communityReportDao()

            var newCount = 0
            for (i in 0 until jsonArray.length()) {
                val obj   = jsonArray.getJSONObject(i)
                val hash  = obj.getString("hash")
                val count = obj.getInt("count")
                if (hash.length != 64 || count < 3) continue

                val existing = dao.findByHash(hash)
                if (existing == null) {
                    dao.insert(CommunityReportEntity(
                        numberHash  = hash,
                        reportCount = count,
                        source      = "community"
                    ))
                    newCount++
                } else if (count > existing.reportCount) {
                    dao.incrementCount(hash, System.currentTimeMillis())
                }
            }

            consentManager.updateLastSync()
            Result.success(workDataOf("new" to newCount, "total" to jsonArray.length()))

        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun parseJsonArray(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }
}

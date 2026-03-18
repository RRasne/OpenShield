package com.openshield.data.repository

import android.util.Log
import com.openshield.data.db.PendingReportEntity
import com.openshield.data.db.SpamDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommunityRepository @Inject constructor(
    private val db: SpamDatabase,
    private val consentManager: ConsentManager
) {
    companion object {
        private const val BASE_URL             = "https://openshield-api.ensarkaralii.workers.dev"
        private const val TAG                  = "CommunityRepo"
        private const val MIN_SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L  // 6 saat
    }

    // ─── Spam Bildirimi — Wi-Fi bağlıysa anında, değilse kuyruğa ─────────────

    suspend fun reportSpam(
        number: String,
        triggeredRules: List<String>,
        isWifiConnected: Boolean
    ) = withContext(Dispatchers.IO) {
        if (!consentManager.communityConsent) return@withContext

        val hash       = sha256(normalizeNumber(number))
        val rulesJson  = JSONArray(triggeredRules).toString()

        if (isWifiConnected) {
            // Anında gönder
            val sent = sendReport(hash, triggeredRules)
            if (!sent) {
                // Gönderim başarısız → kuyruğa ekle
                enqueue(hash, rulesJson)
            }
        } else {
            // Wi-Fi yok → kuyruğa ekle, WifiSyncManager bağlanınca gönderir
            enqueue(hash, rulesJson)
        }
    }

    // ─── Kuyruktaki raporları gönder (Wi-Fi'ye bağlanınca çağrılır) ──────────

    suspend fun flushPendingReports() = withContext(Dispatchers.IO) {
        if (!consentManager.communityConsent) return@withContext

        val pending = db.pendingReportDao().getAll()
        if (pending.isEmpty()) return@withContext

        Log.d(TAG, "flush: ${pending.size} bekleyen rapor")
        for (report in pending) {
            val rules = try {
                val arr = JSONArray(report.triggeredRules)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (_: Exception) { emptyList() }

            val sent = sendReport(report.numberHash, rules)
            if (sent) {
                db.pendingReportDao().deleteById(report.id)
            }
        }
    }

    // ─── Community List Sync (6 saatte bir) ──────────────────────────────────

    suspend fun syncCommunityList() = withContext(Dispatchers.IO) {
        if (!consentManager.communityConsent) return@withContext

        val now = System.currentTimeMillis()
        if (now - consentManager.lastSyncTime < MIN_SYNC_INTERVAL_MS) {
            Log.d(TAG, "sync skipped — too soon")
            return@withContext
        }

        try {
            val since = consentManager.lastSyncTime
            val conn  = openGet("$BASE_URL/community-list?since=$since")

            if (conn.responseCode != 200) { conn.disconnect(); return@withContext }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val arr   = JSONArray(body)
            var added = 0
            for (i in 0 until arr.length()) {
                val hash = arr.getJSONObject(i).getString("hash")
                db.spamNumberDao().insertCommunityHash(hash)
                added++
            }

            consentManager.lastSyncTime = now
            Log.d(TAG, "sync done — $added entries")
        } catch (e: Exception) {
            Log.w(TAG, "sync failed: ${e.message}")
        }
    }

    // ─── İç Yardımcılar ───────────────────────────────────────────────────────

    private suspend fun sendReport(hash: String, rules: List<String>): Boolean {
        return try {
            val body = JSONObject().apply {
                put("numberHash", hash)
                put("triggeredRules", JSONArray(rules))
            }.toString()

            val conn = openPost("$BASE_URL/report", body)
            val code = conn.responseCode
            conn.disconnect()

            (code == 200).also {
                Log.d(TAG, "report ${if (it) "OK" else "FAIL"} → $code (${hash.take(12)}...)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "sendReport exception: ${e.message}")
            false
        }
    }

    private suspend fun enqueue(hash: String, rulesJson: String) {
        db.pendingReportDao().insert(
            PendingReportEntity(numberHash = hash, triggeredRules = rulesJson)
        )
        Log.d(TAG, "kuyruğa eklendi: ${hash.take(12)}...")
    }

    private fun openGet(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod  = "GET"
            connectTimeout = 8000
            readTimeout    = 8000
        }

    private fun openPost(url: String, body: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod  = "POST"
            doOutput       = true
            connectTimeout = 5000
            readTimeout    = 5000
            setRequestProperty("Content-Type", "application/json")
            outputStream.use { it.write(body.toByteArray()) }
        }

    private fun normalizeNumber(number: String) =
        number.replace(Regex("[^0-9+]"), "")

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

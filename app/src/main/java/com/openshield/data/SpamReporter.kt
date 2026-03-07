package com.openshield.data

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
class SpamReporter @Inject constructor() {

    companion object {
        private const val WORKER_URL = "https://openshield-community.ensarkaralii.workers.dev/"
        private const val REPORT_ENDPOINT = "$WORKER_URL/report"
        private const val CSV_ENDPOINT = "$WORKER_URL/community.csv"
        private const val TIMEOUT_MS = 10_000

        fun normalizeNumber(number: String): String =
            number.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")

        fun hashPhoneNumber(number: String): String = sha256(normalizeNumber(number))

        fun sha256(input: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }

    suspend fun report(
        senderNumber: String,
        triggeredRules: List<String>,
        score: Float,
        category: String
    ): ReportResult {
        val numberHash = hashPhoneNumber(senderNumber)
        return reportHashed(numberHash, triggeredRules, score, category)
    }

    suspend fun reportHashed(
        numberHash: String,
        triggeredRules: List<String>,
        score: Float,
        category: String
    ): ReportResult = withContext(Dispatchers.IO) {
        try {
            val safeRules = triggeredRules.filter { it.isNotBlank() }.ifEmpty { listOf("UNKNOWN_RULE") }
            val payload = JSONObject().apply {
                put("number_hash", numberHash)
                put("rules", JSONArray(safeRules))
                put("score", score)
                put("category", category)
            }
            val url = URL(REPORT_ENDPOINT)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray()) }
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                ReportResult.Success(
                    count = json.getInt("count"),
                    inCommunityList = json.getBoolean("in_community_list")
                )
            } else {
                ReportResult.Failed("HTTP $responseCode")
            }
        } catch (e: Exception) {
            ReportResult.Failed(e.message ?: "Unknown error")
        }
    }

    suspend fun fetchCommunityCsv(): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL(CSV_ENDPOINT)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            if (connection.responseCode == 200) connection.inputStream.bufferedReader().readText()
            else null
        } catch (e: Exception) {
            null
        }
    }
}

sealed class ReportResult {
    data class Success(val count: Int, val inCommunityList: Boolean) : ReportResult()
    data class Failed(val reason: String) : ReportResult()
}

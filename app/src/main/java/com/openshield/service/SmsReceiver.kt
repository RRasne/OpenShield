package com.openshield.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.openshield.R
import com.openshield.data.SpamReporter
import com.openshield.data.db.SpamDatabase
import com.openshield.data.repository.SpamRepository
import com.openshield.detection.engine.Classification
import com.openshield.detection.engine.SpamDetectionEngine
import com.openshield.worker.SpamReportUploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages.first().displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }
        if (body.isBlank()) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                val db = SpamDatabase.getInstance(context)
                val repository = SpamRepository(db)
                val engine = SpamDetectionEngine(repository)

                val result = engine.analyze(sender, body)

                when (result.classification) {
                    Classification.SPAM -> {
                        repository.logBlocked(sender = sender, reason = result.reason, score = result.score)
                        enqueueCommunityReport(context, sender, result)
                        // Sessiz bildirim — ses/titresim YOK
                        showSilentNotification(
                            context = context,
                            title = "Spam Engellendi",
                            message = sender,
                            notifId = sender.hashCode()
                        )
                    }
                    Classification.SUSPICIOUS -> {
                        repository.logBlocked(sender = sender, reason = "Supheli: ${result.reason}", score = result.score)
                        // Supheli de sessiz bildirim
                        showSilentNotification(
                            context = context,
                            title = "Supheli SMS",
                            message = sender,
                            notifId = sender.hashCode()
                        )
                    }
                    Classification.CLEAN -> {
                        // No-op
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun enqueueCommunityReport(
        context: Context,
        sender: String,
        result: com.openshield.detection.engine.SpamResult
    ) {
        val numberHash = SpamReporter.hashPhoneNumber(sender)
        val rules = result.reason
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("UNKNOWN_RULE") }

        SpamReportUploadWorker.enqueue(
            context = context,
            numberHash = numberHash,
            rules = rules,
            score = result.score,
            category = detectCategory(rules)
        )
    }

    private fun detectCategory(rules: List<String>): String {
        val text = rules.joinToString(" ").lowercase()
        return when {
            text.contains("gambling") || text.contains("bahis") || text.contains("casino") -> "GAMBLING"
            text.contains("iban") || text.contains("dini") || text.contains("kazand") || text.contains("odul") -> "FRAUD"
            text.contains("url") || text.contains("phish") -> "PHISHING"
            text.contains("sigorta") || text.contains("prom") || text.contains("kampanya") -> "PROMOTION"
            else -> "UNKNOWN"
        }
    }

    private fun showSilentNotification(context: Context, title: String, message: String, notifId: Int) {
        val channelId = "openshield_silent"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "OpenShield",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Spam bildirimleri"
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSound(null)
            .setVibrate(null)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) {
        }
    }
}

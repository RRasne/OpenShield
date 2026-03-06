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
import com.openshield.data.db.SpamDatabase
import com.openshield.data.repository.SpamRepository
import com.openshield.detection.engine.Classification
import com.openshield.detection.engine.SpamDetectionEngine
import com.openshield.detection.rules.RuleEngine
import kotlinx.coroutines.*

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
                        // Sessiz bildirim — ses/titreşim YOK
                        showSilentNotification(
                            context = context,
                            title = "🛡 Spam Engellendi",
                            message = sender,
                            notifId = sender.hashCode()
                        )
                    }
                    Classification.SUSPICIOUS -> {
                        repository.logBlocked(sender = sender, reason = "Şüpheli: ${result.reason}", score = result.score)
                        // Şüpheli de sessiz bildirim
                        showSilentNotification(
                            context = context,
                            title = "⚠️ Şüpheli SMS",
                            message = sender,
                            notifId = sender.hashCode()
                        )
                    }
                    Classification.CLEAN -> { /* dokunma */ }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showSilentNotification(context: Context, title: String, message: String, notifId: Int) {
        val channelId = "openshield_silent"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "OpenShield",
                NotificationManager.IMPORTANCE_LOW  // ← IMPORTANCE_LOW = sessiz, titreşimsiz
            ).apply {
                description = "Spam bildirimleri"
                setSound(null, null)        // ses yok
                enableVibration(false)      // titreşim yok
                enableLights(false)         // ışık yok
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)  // sessiz
            .setSound(null)                                 // ses yok
            .setVibrate(null)                               // titreşim yok
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) { }
    }
}
package com.openshield.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.openshield.MainActivity
import com.openshield.R
import com.openshield.data.db.SpamDatabase
import com.openshield.data.repository.SpamRepository
import com.openshield.detection.engine.Classification
import com.openshield.detection.engine.SpamDetectionEngine
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

        val db = SpamDatabase.getInstance(context)
        val repository = SpamRepository(db, context)

        // ─── Kara liste: BLOCKING sorgu, abortBroadcast() onReceive() içinde ──
        val isBlacklisted = runBlocking(Dispatchers.IO) {
            repository.isInBlacklist(sender)
        }

        if (isBlacklisted) {
            abortBroadcast()  // Diğer uygulamalara gitmesin
            val pendingResult = goAsync()
            scope.launch {
                try {
                    repository.logBlocked(sender = sender, reason = "Kara listede", score = 1.0f)
                    delay(500)  // Mesajın DB'ye yazılmasını bekle
                    deleteFromInbox(context, sender)
                    showBlockedNotification(context, sender, 1.0f)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        // ─── İçerik analizi ───────────────────────────────────────────────────
        val pendingResult = goAsync()
        scope.launch {
            try {
                val engine = SpamDetectionEngine(repository)
                val result = engine.analyze(sender, body)

                when (result.classification) {
                    Classification.SPAM -> {
                        repository.logBlocked(
                            sender = sender,
                            reason = result.reason,
                            score = result.score
                        )
                        delay(800)
                        deleteFromInbox(context, sender)
                        showBlockedNotification(context, sender, result.score)
                    }
                    Classification.SUSPICIOUS -> {
                        repository.logSuspicious(
                            sender = sender,
                            reason = result.reason,
                            score = result.score
                        )
                        // Bildirim YOK — uygulama açılınca dialog çıkar
                    }
                    Classification.CLEAN -> { /* Dokunma */ }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Spam gönderenin mesajını sistem gelen kutusundan siler.
     *
     * Android'de varsayılan SMS uygulaması olmadan tam engelleme yok.
     * Bu fonksiyon mesaj düştükten ~500ms sonra siler.
     * Kullanıcı anlık bildirimi görebilir ama mesaj hemen kaybolur.
     *
     * Bazı üretici ROM'larında (özellikle MIUI) çalışmayabilir.
     */
    private fun deleteFromInbox(context: Context, sender: String) {
        try {
            val uri = Uri.parse("content://sms/inbox")
            val cursor = context.contentResolver.query(
                uri,
                arrayOf("_id"),
                "address = ?",
                arrayOf(sender),
                "date DESC"
            ) ?: return

            cursor.use {
                var deleted = 0
                while (it.moveToNext() && deleted < 5) {
                    val id = it.getLong(0)
                    val count = context.contentResolver.delete(
                        Uri.parse("content://sms/$id"),
                        null, null
                    )
                    if (count > 0) deleted++
                }
            }
        } catch (_: Exception) {
            // Silme izni kısıtlıysa sessizce geç
        }
    }

    private fun showBlockedNotification(context: Context, sender: String, score: Float) {
        val channelId = "openshield_blocked"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Engellenen Spam", NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_tab", "log")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            sender.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("🛡 Spam Engellendi")
            .setContentText("$sender · %${(score * 100).toInt()} spam skoru")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(sender.hashCode(), notification)
        } catch (_: SecurityException) { }
    }
}

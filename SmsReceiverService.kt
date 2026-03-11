package com.openshield.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.openshield.data.model.Classification
import com.openshield.data.model.SmsMessage
import com.openshield.detection.engine.SpamDetectionEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SMS alım servisi.
 * Gelen SMS'i yakalar, spam analizi yapar.
 * SPAM/SUSPICIOUS → bildirim göster (isteğe göre engelle)
 * CLEAN → dokunma, sistem bildirimine bırak
 */
@AndroidEntryPoint
class SmsReceiverService : BroadcastReceiver() {

    @Inject
    lateinit var spamEngine: SpamDetectionEngine

    @Inject
    lateinit var notificationHelper: SpamNotificationHelper

    // BroadcastReceiver async için SupervisorJob
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Çok parçalı mesajları birleştir
        val sender = messages.first().displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }

        // Boş mesajı analiz etme
        if (body.isBlank()) return

        val sms = SmsMessage(sender = sender, body = body)

        val pendingResult = goAsync()
        scope.launch {
            try {
                val result = spamEngine.analyze(sms)

                when (result.classification) {
                    Classification.SPAM -> {
                        notificationHelper.showSpamBlocked(sender, result.score)
                        // İleride: abortBroadcast() ile tamamen engelleme (requires BROADCAST_SMS permission)
                    }
                    Classification.SUSPICIOUS -> {
                        notificationHelper.showSuspiciousWarning(sender, result.score)
                    }
                    Classification.CLEAN -> {
                        // Hiçbir şey yapma, sistem bildirimi devam eder
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

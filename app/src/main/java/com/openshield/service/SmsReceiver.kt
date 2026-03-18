package com.openshield.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.openshield.data.db.SpamDatabase
import com.openshield.data.repository.CommunityRepository
import com.openshield.data.repository.ConsentManager
import com.openshield.data.repository.SpamRepository
import com.openshield.detection.engine.Classification
import com.openshield.detection.engine.SpamDetectionEngine
import com.openshield.detection.rules.SpamTokenExtractor
import com.openshield.worker.WifiSyncManager
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
        val body   = messages.joinToString("") { it.messageBody ?: "" }
        if (body.isBlank()) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                val db             = SpamDatabase.getInstance(context)
                val repository     = SpamRepository(db, context)
                val consentManager = ConsentManager(context)
                val communityRepo  = CommunityRepository(db, consentManager)
                val wifiManager    = WifiSyncManager(context, communityRepo)
                val engine         = SpamDetectionEngine(repository)
                val result         = engine.analyze(sender, body)

                when (result.classification) {

                    Classification.SPAM -> {
                        // Yerel log
                        repository.logBlocked(
                            sender = sender,
                            reason = result.reason,
                            score  = result.score
                        )
                        val safeRules = SpamTokenExtractor.sanitizeRules(result.reason.split(","))

                        // Wi-Fi bağlıysa anında gönder, değilse kuyruğa ekle
                        communityRepo.reportSpam(
                            number          = sender,
                            triggeredRules  = safeRules,
                            isWifiConnected = wifiManager.isWifiConnected()
                        )
                    }

                    Classification.SUSPICIOUS -> {
                        // Kullanıcı kararı için beklet — uygulama açılınca dialog çıkar
                        repository.logSuspicious(
                            sender = sender,
                            reason = result.reason,
                            score  = result.score
                        )
                    }

                    Classification.CLEAN -> { /* dokunma */ }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

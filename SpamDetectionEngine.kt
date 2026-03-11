package com.openshield.detection.engine

import com.openshield.data.model.SmsMessage
import com.openshield.data.model.SpamResult
import com.openshield.data.model.Classification
import com.openshield.data.repository.SpamNumberRepository
import com.openshield.detection.rules.RuleEngine
import com.openshield.detection.ml.TFLiteClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ana spam tespit motoru.
 * Üç katmanlı analiz: numara kontrolü + kural motoru + ML
 *
 * Ağırlıklar:
 *   numberScore  * 0.40
 *   contentScore * 0.35
 *   mlScore      * 0.25
 *
 * Karar eşiği:
 *   > 0.60 → SPAM
 *   0.40–0.60 → SUSPICIOUS
 *   < 0.40 → CLEAN
 */
@Singleton
class SpamDetectionEngine @Inject constructor(
    private val numberRepository: SpamNumberRepository,
    private val ruleEngine: RuleEngine,
    private val mlClassifier: TFLiteClassifier
) {

    companion object {
        private const val WEIGHT_NUMBER = 0.40f
        private const val WEIGHT_CONTENT = 0.35f
        private const val WEIGHT_ML = 0.25f

        private const val THRESHOLD_SPAM = 0.60f
        private const val THRESHOLD_SUSPICIOUS = 0.40f
    }

    /**
     * Bir SMS mesajını analiz eder ve spam sonucu döner.
     * Bu fonksiyon her zaman IO thread'inde çalışmalı.
     */
    suspend fun analyze(sms: SmsMessage): SpamResult = withContext(Dispatchers.IO) {
        val numberScore = analyzeNumber(sms.sender)
        val contentScore = ruleEngine.score(sms.body)
        val mlScore = mlClassifier.classify(sms.body)

        val combinedScore = (numberScore * WEIGHT_NUMBER) +
                            (contentScore * WEIGHT_CONTENT) +
                            (mlScore * WEIGHT_ML)

        val classification = when {
            combinedScore > THRESHOLD_SPAM -> Classification.SPAM
            combinedScore > THRESHOLD_SUSPICIOUS -> Classification.SUSPICIOUS
            else -> Classification.CLEAN
        }

        SpamResult(
            classification = classification,
            score = combinedScore,
            numberScore = numberScore,
            contentScore = contentScore,
            mlScore = mlScore,
            triggeredRules = ruleEngine.getLastTriggeredRules()
        )
    }

    private suspend fun analyzeNumber(sender: String): Float {
        return when {
            numberRepository.isInBlacklist(sender) -> 1.0f
            numberRepository.isInWhitelist(sender) -> 0.0f
            numberRepository.getCommunityReportCount(sender) > 10 -> 0.85f
            numberRepository.getCommunityReportCount(sender) > 3 -> 0.55f
            isShortCode(sender) -> 0.30f  // Bankalar vs. kısa kod kullanır
            else -> 0.0f
        }
    }

    /**
     * Kısa kodlar (4-6 haneli) genellikle meşru servislerden gelir.
     * Düşük ama sıfır olmayan skor verilir, içerik analizi belirleyici olur.
     */
    private fun isShortCode(sender: String): Boolean {
        val digits = sender.replace(Regex("[^0-9]"), "")
        return digits.length in 4..6
    }
}

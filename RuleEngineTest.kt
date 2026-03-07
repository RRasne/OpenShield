package com.openshield.detection

import com.google.common.truth.Truth.assertThat
import com.openshield.data.model.Classification
import com.openshield.data.model.SmsMessage
import com.openshield.detection.rules.RuleEngine
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * RuleEngine unit testleri
 *
 * Temel metrikler:
 * - False positive oranı < %2 (meşru mesajı spam saymama)
 * - False negative oranı < %10 (spamı kaçırmama)
 */
class RuleEngineTest {

    private lateinit var ruleEngine: RuleEngine

    @Before
    fun setup() {
        ruleEngine = RuleEngine()
    }

    // ─── Meşru Mesajlar (False Positive Koruması) ────────────────────────────

    @Test
    fun `bank otp sms should have very low score`() {
        val body = "Bankanızdan doğrulama kodu: 123456. Bu kodu kimseyle paylaşmayın."
        val score = ruleEngine.score(body)
        assertThat(score).isLessThan(0.15f)
    }

    @Test
    fun `simple friend sms should have zero score`() {
        val body = "Yarın saat 3'te görüşelim mi?"
        val score = ruleEngine.score(body)
        assertThat(score).isLessThan(0.1f)
    }

    @Test
    fun `cargo delivery notification should have low score`() {
        val body = "Kargonuz bugün teslim edilecek. Takip: TR123456789"
        val score = ruleEngine.score(body)
        // Kargo içerikli meşru mesajlar biraz skor alır ama spam sayılmamalı
        assertThat(score).isLessThan(0.35f)
    }

    // ─── Spam Mesajlar ───────────────────────────────────────────────────────

    @Test
    fun `prize winner sms should be high score`() {
        val body = "TEBRİKLER! Çekilişimizde 10.000 TL kazandınız! Hemen tıklayın: bit.ly/abc123"
        val score = ruleEngine.score(body)
        assertThat(score).isGreaterThan(0.6f)
    }

    @Test
    fun `phishing sms with iban should be high score`() {
        val body = "Hesabınız bloke edilmiştir. TR12 0006 2000 1130 3200 0001 26 IBAN'ına ödeme yapın."
        val score = ruleEngine.score(body)
        assertThat(score).isGreaterThan(0.55f)
    }

    @Test
    fun `crypto investment spam should score high`() {
        val body = "Kripto yatırımı ile günlük %30 kazanç! Hemen ücretsiz üye ol. Link: tinyurl.com/xyz"
        val score = ruleEngine.score(body)
        assertThat(score).isGreaterThan(0.6f)
    }

    @Test
    fun `all caps spam should get uppercase penalty`() {
        val body = "BÜYÜK KAMPANYA! TÜM ÜRÜNLERDE İNDİRİM! HEMEN TIKLAYIn!"
        val score = ruleEngine.score(body)
        assertThat(score).isGreaterThan(0.4f)
    }

    // ─── Gerçek Spam Örnekleri (Kullanıcı Tarafından Sağlanan) ──────────────

    @Test
    fun `savoy gambling sms with t2m link should be spam`() {
        // Görsel 1: +90 850 771 0195
        val body = "10000TL HESABİNDA\n\n10000TL DEGERİNDE DENEME KAYBEDERSEN\n1000TL İKİNCİ ASAMA İLE SANSİNİ TEKRAR DENE\n\nHAVALE ALT LİMİT 100TL\nSAVOY\nhttps://t2m.io/Savoy26"
        val score = ruleEngine.score(body)
        val rules = ruleEngine.getLastTriggeredRules()
        assertThat(score).isGreaterThan(0.6f)
        assertThat(rules).contains("GAMBLING_BRAND")
        assertThat(rules).contains("CONTAINS_URL")
        assertThat(rules).contains("COMBO:HAVALE+URL")
        assertThat(rules).contains("COMBO:DENEME+BONUS").isFalse() // bonus kelimesi yok bu örnekte
    }

    @Test
    fun `savoy gambling sms variant 2 should be spam`() {
        // Görsel 4: +90 850 966 0674
        val body = "neden gelmedin?\n\n2 TANE DENEME BONUSUN VAR\n1. ETAP 10.000TL\n2. ETAP 1.000TL\n\nYAPACAGIN İLK YATİRİMDA %100 İADE\n\nHAVALE 500TL\n\nSAVOY\nhttps://t2m.io/Savoy30"
        val score = ruleEngine.score(body)
        val rules = ruleEngine.getLastTriggeredRules()
        assertThat(score).isGreaterThan(0.6f)
        assertThat(rules).contains("GAMBLING_BRAND")
        assertThat(rules).contains("COMBO:DENEME+BONUS")
        assertThat(rules).contains("COMBO:HAVALE+URL")
    }

    @Test
    fun `fake charity sms with iban should be spam`() {
        // Görsel 3: ORHANALADAG — sahte SMA bağış dolandırıcılığı
        val body = "Allah rizasi icin Sma hastası evladıma bağışta bulunun.\nTR720001500158007342275906\nOrhan Aladağ\ninstagram.com/smaomerzekii\nIPTAL OALADAG 4609 B372"
        val score = ruleEngine.score(body)
        val rules = ruleEngine.getLastTriggeredRules()
        assertThat(score).isGreaterThan(0.6f)
        assertThat(rules).contains("CONTAINS_IBAN")
        assertThat(rules).contains("COMBO:IBAN+DINI_SOLYEM")
        assertThat(rules).contains("COMBO:IBAN+SOCIAL_MEDIA")
        assertThat(rules).contains("BULK_SMS_CODE")
    }

    @Test
    fun `political bulk sms should be detected`() {
        // Görsel 2: ORHANCERKEZ — belediye başkanı siyasi toplu mesaj
        val body = "Gönüllere huzur, sofralara bereket, bedenlere sağlık getiren on bir ayın sultanı; hoş geldin ya Şehr-i Ramazan.\n\nOrhan ÇERKEZ\nÇekmeköy Belediye Başkanı B018"
        val score = ruleEngine.score(body)
        val rules = ruleEngine.getLastTriggeredRules()
        // Siyasi mesaj — yüksek skor beklenir
        assertThat(score).isGreaterThan(0.4f)
        assertThat(rules).contains("KEYWORD:belediye başkanı")
        assertThat(rules).contains("BULK_SMS_CODE")
    }

    @Test
    fun `ramazan greeting without bulk code should be clean`() {
        // Tanıdık birinden gelen Ramazan mesajı — spam değil
        val body = "Hayırlı Ramazanlar! Sağlıklı ve huzurlu bir ay geçirmeni diliyorum."
        val score = ruleEngine.score(body)
        assertThat(score).isLessThan(0.2f)
    }

    @Test
    fun `url in message should trigger url rule`() {
        val body = "Detaylar için: https://example.com/kampanya"
        ruleEngine.score(body)
        assertThat(ruleEngine.getLastTriggeredRules()).contains("CONTAINS_URL")
    }

    @Test
    fun `iban in message should trigger iban rule`() {
        val body = "TR12 0006 2000 1130 3200 0001 26 numaralı hesaba gönderin"
        ruleEngine.score(body)
        assertThat(ruleEngine.getLastTriggeredRules()).contains("CONTAINS_IBAN")
    }

    @Test
    fun `otp message should trigger whitelist rule`() {
        val body = "Giriş onay kodunuz: 847291"
        ruleEngine.score(body)
        assertThat(ruleEngine.getLastTriggeredRules()).contains("OTP_WHITELIST")
    }
}

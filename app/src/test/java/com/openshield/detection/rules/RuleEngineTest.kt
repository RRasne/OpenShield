package com.openshield.detection.rules

import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private val engine = RuleEngine()

    @Test
    fun bankBonusMessage_shouldStayClean() {
        val sms = "xxyy ile biten kartinizi 04.11.2026 tarihine kadar acik birakma sozunuze karsilik 600,00TL bonusunuz 1 gun icinde yuklenecektir. Bonus son kullanim: 24.03.2026. B002"

        val result = engine.analyze(sms)

        assertTrue(result.score < 0.35f)
    }

    @Test
    fun invoiceMessageWithTrustedDomain_shouldStayClean() {
        val sms = "Degerli Millenicom'lu, Faturanizi https://m.milleni.com.tr/xxxxxxxxxxx linkine tiklayarak hicbir sifre girmeden ve cagri merkezimizi aramaya gerek kalmadan tum detaylariyla inceleyebilirsiniz. yyyyyy numarali aboneliginize ait 13.03.2026 son odeme tarihli faturaniz 461.67 TL'dir. Faturanizi simdi odemek icin: https://m.milleni.com.tr/r/fatura Odeme yaptiysaniz lutfen bu mesaji dikkate almayiniz. B002"

        val result = engine.analyze(sms)

        assertTrue(result.score < 0.35f)
    }

    @Test
    fun insurancePromotionSpam_shouldBeSpam() {
        val sms = "Ayin son gunu fiyatlar degismeden Tamamlayici Saglik Sigortasi ile sagligini guvenceye al! Hemen bilgi almak icin arayin: 0216 250 76 47 SMSRET:SNET RET 7889 MS:0770015174700010 http://sgrtm.net Bilgi 4442400 B016"

        val result = engine.analyze(sms)

        assertTrue(result.score >= 0.60f)
    }

    @Test
    fun installmentInsuranceSpam_shouldBeSpam() {
        val sms = "Saglik Sigortasinda Buyuk FIRSAT! 12 Ay Taksit ve Sifir Vade Farkiyla butce dostu secenekler Sigortam.net te. Bilgi icin 02162507647 yi ara, teklif icin hemen tikla! http://sgrtm.net/7W2Y4995810UR SMSRET:SNET RET 7889 MS:0770015174700010 Bilgi 4442400 B016"

        val result = engine.analyze(sms)

        assertTrue(result.score >= 0.60f)
    }
}

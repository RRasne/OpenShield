package com.openshield.detection.rules

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kural tabanlı içerik analizi — v4
 *
 * v4 DEĞİŞİKLİKLER:
 * - Kasko/vade hatırlatma: SMSRET olan kişisel hatırlatmalar → SPAM (izinsiz reklam,
 *   kullanıcı beyaz listeye ekleyebilir)
 * - "Mersis" kodu tespiti düzeltildi (Mersis0770... formatı)
 * - Türkçe normalizasyon genişletildi
 * - Banka bonus beyaz listesi korundu
 */
@Singleton
class RuleEngine @Inject constructor() {

    private val triggeredRules = mutableListOf<String>()

    // ─── Türkçe Normalizasyon ──────────────────────────────────────────────────

    private fun normalizeTurkish(text: String): String = text
        .replace("tiklayin", "tıklayın")
        .replace("tikla", "tıkla")
        .replace("son gun", "son gün")
        .replace("gunu", "günü")
        .replace("yatirim", "yatırım")
        .replace("firsat", "fırsat")
        .replace("ucretsiz", "ücretsiz")
        .replace("odul", "ödül")
        .replace("sans", "şans")
        .replace("bagis", "bağış")
        .replace("kazanc", "kazanç")
        .replace("cekilis", "çekiliş")
        .replace("katil", "katıl")
        .replace("kazanin", "kazanın")

    // ─── Beyaz Liste: OTP / Banka Doğrulama ───────────────────────────────────

    private val otpPattern = Regex(
        """(\d{4,8})\s*(kod|code|otp|şifre|sifre|doğrulama|dogrulama|onay)""",
        RegexOption.IGNORE_CASE
    )
    private val bankVerifyPattern = Regex(
        """(bankanız|bankaniz|hesabınız|hesabiniz|kartınız|kartiniz).{0,50}(kod|code|şifre|sifre|onay)""",
        RegexOption.IGNORE_CASE
    )

    // ─── Beyaz Liste: Banka Bonus / Puan Bildirimi ────────────────────────────

    private val bankBonusPattern = Regex(
        """bonus(unuz|u|ları)?\s.{0,60}(yuklen|yüklen|karsilik|karşılık|son kullan)""",
        RegexOption.IGNORE_CASE
    )
    private val bankIndicatorPattern = Regex(
        """(kartiniz|kartınız|hesabiniz|hesabınız|borcunuz|taksitiniz|son kullanim tarihi|son kullanım tarihi).{0,80}(bonus|puan|tl|lira)""",
        RegexOption.IGNORE_CASE
    )

    // ─── Spam Anahtar Kelimeleri ───────────────────────────────────────────────

    private val spamKeywords = mapOf(
        // Kumar / Bahis
        "deneme bonusu"     to 0.98f,
        "havale alt limit"  to 0.98f,
        "deneme"            to 0.60f,
        "bonus"             to 0.50f,   // Düşük — banka mesajı false positive riski
        "havale"            to 0.75f,
        "etap"              to 0.55f,
        "yatırım"           to 0.70f,
        "yatirim"           to 0.70f,
        "%100 iade"         to 0.92f,
        "ilk yatırımda"     to 0.88f,
        "ilk yatirimda"     to 0.88f,
        "papara"            to 0.62f,
        "usdt"              to 0.72f,
        "kripto"            to 0.67f,
        "bitcoin"           to 0.67f,

        // Kazanç / Ödül
        "kazandınız"        to 0.92f,
        "kazan"             to 0.65f,
        "ödül"              to 0.80f,
        "odul"              to 0.80f,
        "hediye"            to 0.60f,
        "ücretsiz"          to 0.52f,
        "ucretsiz"          to 0.52f,
        "tıklayın"          to 0.70f,
        "tiklayin"          to 0.70f,
        "hemen tıkla"       to 0.88f,
        "hemen tikla"       to 0.88f,
        "tıkla"             to 0.55f,   // Tek başına orta risk
        "tikla"             to 0.55f,
        "son gün"           to 0.72f,
        "son gun"           to 0.72f,
        "son şans"          to 0.82f,
        "son sans"          to 0.82f,
        "indirim"           to 0.38f,
        "kampanya"          to 0.38f,
        "fırsat"            to 0.45f,
        "firsat"            to 0.45f,

        // Borç / Tehdit
        "borç"              to 0.52f,
        "borc"              to 0.52f,
        "icra"              to 0.72f,
        "avukat"            to 0.62f,
        "kazanç"            to 0.57f,

        // Sahte bağış
        "allah rızası"      to 0.78f,
        "allah rizasi"      to 0.78f,
        "bağışta bulunun"   to 0.82f,
        "bagista bulunun"   to 0.82f,
        "bağış"             to 0.47f,
        "bagis"             to 0.47f,
        "hasta"             to 0.22f,
        "sma"               to 0.32f,

        // Siyasi spam
        "belediye başkanı"  to 0.72f,
        "belediye baskani"  to 0.72f,
        "milletvekili"      to 0.62f,
        "aday"              to 0.37f,
        "seçim"             to 0.42f,
        "secim"             to 0.42f,

        // İngilizce spam
        "won"               to 0.78f,
        "winner"            to 0.82f,
        "free"              to 0.52f,
        "click here"        to 0.88f,
        "prize"             to 0.78f,
        "urgent"            to 0.62f,
        "suspended"         to 0.72f,
        "deposit"           to 0.62f,
        "withdraw"          to 0.62f,
    )

    // ─── Regex Kalıpları ───────────────────────────────────────────────────────

    private val urlPatterns = listOf(
        Regex("""https?://\S+"""),
        Regex("""bit\.ly/\S+"""),
        Regex("""tinyurl\.com/\S+"""),
        Regex("""t2m\.io/\S+"""),
        Regex("""t\.me/\S+"""),
        Regex("""\b\w{2,20}\.net/\w+"""),      // sgrtm.net/xxxxx
        Regex("""\b\w{2,10}\.xyz\b"""),
        Regex("""\b\w{2,10}\.tk\b"""),
        Regex("""\b\w{2,10}\.click\b"""),
        Regex("""\b\w{2,10}\.bet\b"""),
        Regex("""\b\w{2,10}\.casino\b"""),
    )

    private val suspiciousLinkServices = listOf(
        "t2m.io", "bit.ly", "tinyurl", "rebrand.ly", "cutt.ly", "sgrtm.net"
    )

    private val ibanPattern = Regex(
        """TR\d{2}\s?\d{4}\s?\d{4}\s?\d{4}\s?\d{4}\s?\d{4}\s?\d{2}"""
    )

    // Cep telefonu (05XX)
    private val mobilePhonePattern = Regex(
        """(\+90|0090|090)?\s?5\d{2}[\s\-]?\d{3}[\s\-]?\d{2}[\s\-]?\d{2}"""
    )

    // Sabit hat / çağrı merkezi (0216, 0212, 0850, 444x)
    private val fixedLinePattern = Regex(
        """0(2\d{2}|850|444)\s?\d{3}\s?\d{2}\s?\d{2}"""
    )

    // Toplu SMS opt-out kodu: B016, B018, B372 vb.
    private val bulkSmsCodePattern = Regex("""\bB\d{3,4}\b""")

    // Reklam SMS opt-out kodu: "SMSRET", "SNET RET", "SMS iptal"
    private val smsRetPattern = Regex(
        """SMSRET|SNET\s*RET|SMSIPTAL|SMS\s*iptal""",
        RegexOption.IGNORE_CASE
    )

    // Reklam platform/firma kodu: "MS:0770015174700010" veya "Mersis0770..."
    private val adMsgIdPattern = Regex(
        """(MS:|Mersis)\s?0\d{8,}"""
    )

    // Kumar sitesi markaları
    private val gamblingBrandPattern = Regex(
        """\b(savoy|betist|bets10|betturkey|casinomaxi|mobilbahis|superbetin|betboo|casino|bahis|poker|slot)\b""",
        RegexOption.IGNORE_CASE
    )

    // ─── Kullanıcıya Türkçe Açıklama ──────────────────────────────────────────

    fun toHumanReadable(rules: List<String>): String {
        if (rules.isEmpty()) return "Spam içerik tespit edildi"
        if (rules.contains("OTP_WHITELIST")) return "Güvenli doğrulama kodu"
        if (rules.contains("BANK_BONUS_WHITELIST")) return "Banka bonus bildirimi"

        val descriptions = rules.mapNotNull { rule ->
            when {
                rule == "GAMBLING_BRAND"            -> "Kumar/bahis sitesi"
                rule == "CONTAINS_URL"              -> "Şüpheli link içeriyor"
                rule == "CONTAINS_IBAN"             -> "IBAN numarası içeriyor"
                rule == "BULK_SMS_CODE"             -> "Toplu reklam mesajı"
                rule == "SMSRET_CODE"               -> "İzinsiz reklam SMS'i"
                rule == "AD_MSG_ID"                 -> "Reklam şirketi kodu"
                rule == "COMBO:HAVALE+URL"          -> "Havale talebi + şüpheli link"
                rule == "COMBO:DENEME+BONUS"        -> "Kumar sitesi teklifi"
                rule == "COMBO:IBAN+DINI_SOLYEM"    -> "Dini söylemli sahte bağış"
                rule == "COMBO:IBAN+SOCIAL_MEDIA"   -> "Sosyal medya destekli dolandırıcılık"
                rule == "COMBO:URL+PHONE+BULK"      -> "Reklam: link + numara + toplu mesaj"
                rule.startsWith("HIGH_UPPERCASE")   -> "Tamamı büyük harf"
                rule.startsWith("MEDIUM_UPPERCASE") -> null
                rule == "MOBILE_PHONE_IN_BODY"      -> "İçerikte telefon numarası"
                rule == "FIXED_PHONE_IN_BODY"       -> "Çağrı merkezi numarası"
                rule == "SUSPICIOUS_LINK_SERVICE"   -> "Kısaltılmış şüpheli link"
                rule.startsWith("KEYWORD:")         -> keywordToTurkish(rule.removePrefix("KEYWORD:"))
                else -> null
            }
        }.distinct()

        return if (descriptions.isEmpty()) "Spam içerik tespit edildi"
        else descriptions.take(3).joinToString(" · ")
    }

    private fun keywordToTurkish(keyword: String): String? = when (keyword) {
        "deneme bonusu", "deneme"                   -> "Kumar bonusu teklifi"
        "bonus"                                     -> null
        "havale", "havale alt limit"                -> "Havale talebi"
        "kazandınız", "kazan", "ödül", "odul"       -> "Sahte kazanç bildirimi"
        "ücretsiz", "ucretsiz", "hediye"            -> "Ücretsiz/hediye teklifi"
        "tıklayın", "tiklayin",
        "hemen tıkla", "hemen tikla",
        "tıkla", "tikla"                            -> "Tıklama yönlendirmesi"
        "son gün", "son gun",
        "son şans", "son sans"                      -> "Aciliyet baskısı"
        "fırsat", "firsat"                          -> "Fırsat teklifi"
        "yatırım", "yatirim"                        -> "Yatırım teklifi"
        "%100 iade",
        "ilk yatırımda", "ilk yatirimda"            -> "Geri iade teklifi"
        "papara", "usdt", "bitcoin", "kripto"       -> "Kripto/anonim ödeme"
        "icra", "borç", "borc", "avukat"            -> "Borç/icra tehdidi"
        "allah rızası", "allah rizasi",
        "bağışta bulunun", "bagista bulunun",
        "bağış", "bagis"                            -> "Bağış talebi"
        "belediye başkanı", "belediye baskani",
        "milletvekili"                              -> "Siyasi toplu mesaj"
        "won", "winner", "prize"                    -> "Sahte ödül bildirimi"
        "click here", "urgent"                      -> "İngilizce spam"
        "suspended", "deposit", "withdraw"          -> "Finansal dolandırıcılık"
        "indirim", "kampanya"                       -> null
        else                                        -> null
    }

    // ─── Ana Skorlama ──────────────────────────────────────────────────────────

    fun score(body: String): Float {
        triggeredRules.clear()
        val lowerBody = body.lowercase()
        val normalizedBody = normalizeTurkish(lowerBody)
        var totalScore = 0.0f

        // ── Beyaz Liste (erken çıkış) ─────────────────────────────────────────

        if (isLikelyOtp(body)) {
            triggeredRules.add("OTP_WHITELIST")
            return 0.05f
        }

        if (bankBonusPattern.containsMatchIn(lowerBody) ||
            bankIndicatorPattern.containsMatchIn(lowerBody)) {
            triggeredRules.add("BANK_BONUS_WHITELIST")
            return 0.08f
        }

        // ── Anahtar Kelime Skorlaması ─────────────────────────────────────────

        var keywordScore = 0.0f
        for ((keyword, weight) in spamKeywords) {
            if (normalizedBody.contains(keyword)) {
                keywordScore = minOf(keywordScore + weight * 0.3f, 1.0f)
                triggeredRules.add("KEYWORD:$keyword")
            }
        }
        totalScore += keywordScore * 0.5f

        // ── URL / Link ────────────────────────────────────────────────────────

        val hasUrl = urlPatterns.any { it.containsMatchIn(body) }
        if (hasUrl) { totalScore += 0.30f; triggeredRules.add("CONTAINS_URL") }

        if (!hasUrl && suspiciousLinkServices.any { lowerBody.contains(it) }) {
            totalScore += 0.20f; triggeredRules.add("SUSPICIOUS_LINK_SERVICE")
        }

        // ── IBAN ─────────────────────────────────────────────────────────────

        val hasIban = ibanPattern.containsMatchIn(body)
        if (hasIban) { totalScore += 0.40f; triggeredRules.add("CONTAINS_IBAN") }

        // ── Kumar Markası ─────────────────────────────────────────────────────

        if (gamblingBrandPattern.containsMatchIn(body)) {
            totalScore += 0.55f; triggeredRules.add("GAMBLING_BRAND")
        }

        // ── Toplu / Reklam SMS Kodları ────────────────────────────────────────

        val hasBulkCode = bulkSmsCodePattern.containsMatchIn(body)
        if (hasBulkCode) { totalScore += 0.35f; triggeredRules.add("BULK_SMS_CODE") }

        val hasSmsRet = smsRetPattern.containsMatchIn(body)
        if (hasSmsRet) { totalScore += 0.45f; triggeredRules.add("SMSRET_CODE") }

        if (adMsgIdPattern.containsMatchIn(body)) {
            totalScore += 0.25f; triggeredRules.add("AD_MSG_ID")
        }

        // ── Telefon Numaraları ────────────────────────────────────────────────

        val hasMobilePhone = mobilePhonePattern.containsMatchIn(body)
        if (hasMobilePhone) { totalScore += 0.15f; triggeredRules.add("MOBILE_PHONE_IN_BODY") }

        val hasFixedPhone = fixedLinePattern.containsMatchIn(body)
        if (hasFixedPhone) { totalScore += 0.10f; triggeredRules.add("FIXED_PHONE_IN_BODY") }

        // ── Combo Bonuslar ────────────────────────────────────────────────────

        if (hasIban && (lowerBody.contains("allah") || lowerBody.contains("riza") || lowerBody.contains("hayir"))) {
            totalScore += 0.25f; triggeredRules.add("COMBO:IBAN+DINI_SOLYEM")
        }

        if (hasIban && (lowerBody.contains("instagram") || lowerBody.contains("twitter") || lowerBody.contains("tiktok"))) {
            totalScore += 0.18f; triggeredRules.add("COMBO:IBAN+SOCIAL_MEDIA")
        }

        if (normalizedBody.contains("havale") && hasUrl) {
            totalScore += 0.28f; triggeredRules.add("COMBO:HAVALE+URL")
        }

        if (normalizedBody.contains("deneme") && normalizedBody.contains("bonus")) {
            totalScore += 0.45f; triggeredRules.add("COMBO:DENEME+BONUS")
        }

        if (hasUrl && (hasFixedPhone || hasMobilePhone) && hasBulkCode) {
            totalScore += 0.20f; triggeredRules.add("COMBO:URL+PHONE+BULK")
        }

        // ── Format Kontrolleri ────────────────────────────────────────────────

        val upperRatio = body.count { it.isUpperCase() }.toFloat() / body.length.coerceAtLeast(1)
        if (upperRatio > 0.5f) {
            totalScore += 0.20f; triggeredRules.add("HIGH_UPPERCASE:${(upperRatio * 100).toInt()}%")
        } else if (upperRatio > 0.3f) {
            totalScore += 0.08f; triggeredRules.add("MEDIUM_UPPERCASE:${(upperRatio * 100).toInt()}%")
        }

        val emojiCount = body.codePoints()
            .filter { cp -> cp in 0x1F300..0x1FAFF || cp in 0x2600..0x27BF }
            .count()
        if (emojiCount > 3) { totalScore += 0.10f; triggeredRules.add("EMOJI_HEAVY:$emojiCount") }

        return totalScore.coerceIn(0.0f, 1.0f)
    }

    fun getLastTriggeredRules(): List<String> = triggeredRules.toList()

    private fun isLikelyOtp(body: String): Boolean =
        otpPattern.containsMatchIn(body) || bankVerifyPattern.containsMatchIn(body)
}

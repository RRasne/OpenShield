# AGENTS.md — OpenShield

Bu dosya, AI kod asistanlarının (Claude, Copilot, Cursor, vb.) bu proje üzerinde çalışırken
uyması gereken kuralları ve proje mimarisini açıklar.

---

## 🛡️ Proje Kimliği

**OpenShield** — Android için tamamen yerel, gizlilik öncelikli SMS spam filtresi.
- Hiçbir veri internete gönderilmez
- Tüm tespit cihaz üzerinde çalışır
- Açık kaynak (GitHub) + Play Store dağıtımı

---

## 📁 Proje Yapısı

```
OpenShield/
├── app/src/main/
│   ├── java/com/openshield/
│   │   ├── data/
│   │   │   ├── db/             # Room veritabanı (DAO, Database)
│   │   │   ├── model/          # Entity sınıfları
│   │   │   └── repository/     # Repository pattern
│   │   ├── detection/
│   │   │   ├── engine/         # Ana spam tespit motoru
│   │   │   ├── rules/          # Kural tabanlı filtreler
│   │   │   └── ml/             # On-device ML (TFLite)
│   │   ├── ui/
│   │   │   ├── screens/        # Jetpack Compose ekranları
│   │   │   ├── components/     # Yeniden kullanılabilir bileşenler
│   │   │   └── theme/          # Renk, tipografi, tema
│   │   ├── service/            # SmsRetrieverService
│   │   ├── worker/             # WorkManager görevleri
│   │   └── util/               # Yardımcı sınıflar
│   └── res/
│       ├── xml/                # SMS filtre provider XML
│       └── values/             # String, color, dimen
├── docs/                       # Teknik belgeler
└── AGENTS.md                   # Bu dosya
```

---

## ⚙️ Teknoloji Kararları

| Karar | Seçim | Sebep |
|---|---|---|
| UI | Jetpack Compose | Modern, az kod |
| Veritabanı | Room + SQLite | Offline, hızlı |
| DI | Hilt | Google önerisi |
| Async | Kotlin Coroutines + Flow | Reactive, test edilebilir |
| ML | TensorFlow Lite | On-device, offline |
| SMS API | `SmsMessage` + Manifest filter | Android standart |
| Build | Gradle KTS | Type-safe |
| Min SDK | 26 (Android 8.0) | SmsRetriever desteği |
| Target SDK | 34 (Android 14) | Play Store zorunluluğu |

---

## 🧠 Spam Tespit Mimarisi

### Katmanlı Skorlama Sistemi

```
SMS Gelir
    │
    ▼
[1. NUMARA KONTROLÜ] ──── SpamNumberRepository
    │  • Yerel kara liste (Room DB)
    │  • Topluluk veritabanı (bundled assets)
    │  • Regex: +90 5XX, kısa kodlar
    │
    ▼
[2. İÇERİK ANALİZİ] ──── RuleEngine
    │  • Anahtar kelime skoru (URL, "ödül", "kazandınız"...)
    │  • Regex desenleri (IBAN, link kalıpları)
    │  • Dil tespiti (TR/EN spam kalıpları)
    │
    ▼
[3. ML SKORU] ──────────── TFLiteClassifier
    │  • Naive Bayes veya küçük BERT (TFLite)
    │  • Model: assets/spam_model.tflite
    │  • Threshold: 0.75
    │
    ▼
[4. KOMBİNE KARAR] ──────── SpamScoreAggregator
       • numara_skoru * 0.4 + içerik_skoru * 0.35 + ml_skoru * 0.25
       • > 0.6 → SPAM
       • 0.4–0.6 → ŞÜPHELİ
       • < 0.4 → TEMİZ
```

### Topluluk Verisi
- `assets/community_spam_numbers.db` — uygulama ile birlikte gelen, periyodik güncellenen SQLite
- Kullanıcı "spam bildir" dediğinde → yerel DB'e eklenir
- **İnternet bağlantısı asla gerekmez**

---

## 📐 Kod Standartları

### Genel
- Kotlin, Java yazmayın
- `var` yerine `val` tercih edin
- Null safety: `!!` kullanmaktan kaçının, `?.let` veya `?: return` kullanın
- Tüm suspend fonksiyonlar `Dispatchers.IO`'da çalışmalı

### Naming
```kotlin
// ✅ Doğru
class SpamDetectionEngine
fun analyzeMessage(sms: SmsMessage): SpamResult
val spamThreshold = 0.6f

// ❌ Yanlış
class Manager
fun check(s: String): Boolean
var threshold = 0.6
```

### Repository Pattern
```kotlin
// Her repository interface + impl olmalı
interface SpamNumberRepository {
    suspend fun isSpamNumber(number: String): Boolean
    suspend fun reportAsSpam(number: String, reason: String)
    fun getReportedNumbers(): Flow<List<SpamNumber>>
}
```

### Compose
- Her ekran = `@Composable fun XxxScreen(viewModel: XxxViewModel)`
- ViewModel state = `StateFlow<UiState>` (sealed class)
- Side effect = `LaunchedEffect`, `SideEffect`
- Preview ekleyin: `@Preview(showBackground = true)`

---

## 🔐 Gizlilik Kuralları (KRİTİK)

1. **SMS içeriği asla loglanmaz** — `Log.d` ile SMS body yazmak yasak
2. **Numara hashing** — DB'de telefon numaraları SHA-256 hash olarak saklanır, düz metin değil
3. **İzin minimalizmi** — Sadece `RECEIVE_SMS` ve `READ_SMS`, başka hiçbir ağ izni yok
4. **Veri silme** — Kullanıcı istediğinde tüm yerel veri silinebilmeli

```kotlin
// Numara kaydetme — her zaman hash kullan
fun hashPhoneNumber(number: String): String {
    val normalized = number.replace(Regex("[^0-9+]"), "")
    return MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
```

---

## 🧪 Test Gereksinimleri

- Her `detection/` sınıfı için unit test zorunlu
- Test coverage hedefi: **%80+**
- Spam/ham test dataset: `test/resources/sms_dataset.json`
- False positive oranı: **< %2** (meşru mesajı spam sayma)
- False negative oranı: **< %10** (spamı kaçırma)

```kotlin
// Test şablonu
@Test
fun `bank verification sms should not be spam`() {
    val sms = SmsMessage(body = "Bankanızdan doğrulama kodu: 123456", sender = "+905001234567")
    val result = spamEngine.analyze(sms)
    assertThat(result.classification).isEqualTo(Classification.CLEAN)
}
```

---

## 🚫 Yapılmaması Gerekenler

- `INTERNET` permission ekleme — gizlilik ihlali
- SMS içeriğini SharedPreferences'a kaydetme
- Numara veya mesajı düz metin DB'e yazma
- `Thread.sleep()` kullanma, coroutine kullan
- Hardcoded string kullanma, `strings.xml`'e taşı
- `Activity` içine iş mantığı yazma, ViewModel'e taşı

---

## 🔄 Git Workflow

```
main          ← kararlı, release
  └── develop ← aktif geliştirme
        ├── feature/xxx  ← yeni özellik
        ├── fix/xxx      ← hata düzeltme
        └── chore/xxx    ← bağımlılık, yapılandırma
```

### Commit Mesajları (Conventional Commits)
```
feat(detection): add TFLite spam classifier
fix(ui): correct filter toggle state on config change
chore(deps): update Room to 2.6.1
docs(agents): update scoring weights
```

---

## 📋 Açık Görevler (Backlog)

- [ ] `SpamDetectionEngine` — temel skor birleştirme
- [ ] `RuleEngine` — Türkçe spam anahtar kelime listesi
- [ ] `TFLiteClassifier` — model entegrasyonu
- [ ] `SmsReceiverService` — SMS dinleme servisi
- [ ] Ana ekran UI (Compose)
- [ ] Filtre listesi ekranı
- [ ] Beyaz liste / kara liste yönetimi
- [ ] Onboarding akışı
- [ ] Play Store metadata
- [ ] F-Droid uyumluluğu

---

*Son güncelleme: 2026-03 | Versiyon: 0.1.0-alpha*

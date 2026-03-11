# AGENTS.md — OpenShield

Bu dosya, AI kod asistanlarının (Claude, Copilot, Cursor vb.) OpenShield üzerinde çalışırken uyması gereken kuralları ve güncel mimariyi özetler.

---

## Proje Kimliği

**OpenShield** — Android için gizlilik öncelikli SMS spam filtresi.

Temel ilkeler:
- Spam tespiti tamamen cihaz üzerinde çalışır (offline-first).
- SMS içeriği ve gerçek telefon numarası hiçbir zaman dış servislere gönderilmez.
- Topluluk katkısı **isteğe bağlıdır** ve yalnızca kullanıcı onayıyla aktif olur.
- Şüpheli mesajlar sessizce tutulur; kullanıcı uygulamayı açınca karar verir.

---

## Güncel Mimari

```
app/src/main/java/com/openshield/
├── data/
│   ├── db/
│   │   └── SpamDatabase.kt          # Room — 5 tablo (aşağıda detay)
│   ├── repository/
│   │   └── SpamNumberRepository.kt  # Tek repository, tüm DB erişimi buradan
│   ├── BundledSpamImporter.kt       # assets CSV → Room import (uygulama ilk açılışında)
│   └── SpamReporter.kt              # Anonim topluluk raporu gönderici
├── detection/
│   ├── engine/
│   │   └── SpamDetectionEngine.kt   # Skor birleştirme: numara + kural + ML
│   └── rules/
│       └── RuleEngine.kt            # TR/EN kural seti, combo kurallar
├── service/
│   └── SmsReceiver.kt               # BroadcastReceiver — analiz + sessiz bildirim
├── worker/
│   ├── CommunityUpdateWorker.kt     # Wi-Fi'de topluluk listesini çeker (delta)
│   └── WifiSyncManager.kt           # NetworkCallback — Wi-Fi bağlanınca tetikler
├── util/
│   └── ConsentManager.kt            # Kullanıcı onayı + sync zamanı yönetimi
├── ui/
│   ├── MainActivity.kt              # Ana ekran — 5 sekme (Compose)
│   ├── MainViewModel.kt             # StateFlow tabanlı UI state
│   ├── MessageHistoryScreen.kt      # SMS geçmişi — gönderici gruplaması + işaretleme
│   └── SuspiciousReviewDialog.kt    # Şüpheli mesaj karar dialogu
└── OpenShieldApp.kt                 # Application — WifiSyncManager.register()
```

---

## Veritabanı Tabloları (Room, v3)

| Tablo | Amaç | Numara formatı |
|---|---|---|
| `spam_numbers` | Kullanıcının kara listesi | Düz metin (kullanıcı girişi) |
| `whitelist` | Kullanıcının beyaz listesi | Düz metin (kullanıcı girişi) |
| `blocked_log` | Engelleme geçmişi | Gönderici düz metin · skor · neden |
| `community_reports` | Topluluk raporları (gelen + bildirilen) | **SHA-256 hash** |
| `pending_review` | Şüpheli mesajlar — kullanıcı kararı bekleniyor | Gönderici · skor · neden (SMS body **yok**) |

**Migration geçmişi:** v1 → v2: `community_reports` eklendi. v2 → v3: `pending_review` eklendi.

---

## Skor Eşikleri ve Sınıflandırma Akışı

```
analyze(sender, body)
    │
    ├─ isInWhitelist? → CLEAN (0.0)
    ├─ isInBlacklist? → SPAM (1.0)
    │
    ├─ communityReportCount > 10 → numberScore = 0.85
    ├─ communityReportCount > 3  → numberScore = 0.55
    ├─ shortCode (4-6 hane)      → numberScore = 0.30
    │
    ├─ RuleEngine.score(body)    → contentScore
    │
    └─ Ağırlıklı toplam:
         numara * 0.40 + içerik * 0.35 + ml * 0.25
              │
              ├─ > 0.60 → SPAM → blocked_log'a yaz, sessiz bildirim
              ├─ 0.40–0.60 → SUSPICIOUS → pending_review'a yaz, bildirim YOK
              └─ < 0.40 → CLEAN → hiçbir şey yapma
```

---

## Wi-Fi Sync Akışı

```
Wi-Fi bağlandı (WifiSyncManager.NetworkCallback)
    │
    ├─ ConsentManager.isCommunityConsentGiven? → hayır → dur
    ├─ ConsentManager.isSyncDue() (6 saat geçti mi?) → hayır → dur
    │
    └─ CommunityUpdateWorker.runOnce()
            │
            ├─ Tekrar consent kontrol (kuyruktayken iptal edilmiş olabilir)
            ├─ GET /community-list?since=<lastSync>  (delta)
            ├─ Hash listesini Room'a yaz
            └─ ConsentManager.updateLastSync()
```

**Kural:** Periyodik WorkManager worker yok. Sadece `WifiSyncManager` → `CommunityUpdateWorker` zinciri.

---

## Şüpheli Mesaj Akışı

```
SMS alındı → SUSPICIOUS sınıflandı
    │
    └─ repository.logSuspicious(sender, reason, score)
            │  (SMS body kaydedilmez)
            ▼
        pending_review tablosu

Kullanıcı uygulamayı açtı
    │
    └─ SuspiciousReviewDialog (pendingReviews.firstOrNull())
            │
            ├─ "Spam" → blocked_log + community_reports + pending_review'dan sil
            └─ "Değil" → sadece pending_review'dan sil
```

---

## Gizlilik Kuralları (Kritik)

1. **SMS body asla loglanmaz** — `Log.d`, DB, SharedPreferences, ağ payload içinde dahil.
2. **pending_review tablosuna SMS body yazılmaz** — sadece `sender`, `reason`, `score`, `receivedAt`.
3. **Topluluk gönderiminde** yalnızca `number_hash` (SHA-256), `rules`, `score`, `category` gönderilir.
4. **Asla gönderilmez:** düz telefon numarası, SMS metni, cihaz kimliği, konum, IP.
5. **Topluluk paylaşımı opt-in** — `ConsentManager.isCommunityConsentGiven == false` ise hiçbir ağ isteği yapılmaz.
6. **Wi-Fi zorunlu** — mobil veri üzerinden topluluk isteği yapılmaz (`NetworkType.UNMETERED`).

---

## Kod Standartları

- Kotlin kullan, Java ekleme.
- `var` yerine mümkün olduğunca `val`.
- `!!` kullanma; `?: return`, `?.let` tercih et.
- `suspend` I/O işleri `Dispatchers.IO` üzerinde çalışmalı.
- İş mantığını `Activity` içine gömme; `Repository` / `Engine` / `ViewModel` katmanında tut.
- Yeni DB tablosu eklenince migration yazılması zorunludur — `fallbackToDestructiveMigration()` kullanılmaz.

---

## UI Bileşen Kararları

- **Bundled/community spam numaraları kullanıcıya gösterilmez** — kara liste sadece `isUserAdded = true` kayıtları gösterir.
- **Offline/online durumu UI'da gösterilmez** — gizlilik özelliğini reklam etmekten kaçın.
- **Şüpheli mesaj dialogu kapatılamaz** — kullanıcı mutlaka "Spam" veya "Değil" seçmelidir.
- **Bildirimler sessiz** — `IMPORTANCE_LOW`, ses ve titreşim kapalı.

---

## Test Gereksinimleri

- `detection/` altındaki her değişiklikte unit test zorunludur.
- En az şu senaryolar test edilmeli:
  - Meşru banka OTP → `CLEAN` (false positive koruması)
  - Kumar sitesi SMS (deneme+bonus+havale+URL) → `SPAM`
  - Sahte bağış SMS (IBAN+dini söylem+sosyal medya) → `SPAM`
  - Sınır durum → `SUSPICIOUS` bandında
- `RuleEngine.getLastTriggeredRules()` ile hangi kuralların tetiklendiği doğrulanmalı.

---

## Yapılmaması Gerekenler

- SMS içeriğini veya gerçek numarayı uzak servise göndermek.
- `INTERNET` iznini topluluk dışında kullanmak.
- `pending_review`'a SMS body kaydetmek.
- Periyodik WorkManager worker eklemek (Wi-Fi sync'i `WifiSyncManager` yönetir).
- `fallbackToDestructiveMigration()` kullanmak — her DB versiyonu için migration yazılmalı.
- `Thread.sleep()` kullanmak (coroutine kullan).
- `AppModule` dışında `@Singleton` scope'u Hilt olmadan yönetmeye çalışmak.

---

## Git ve Commit

- Branch akışı: `main` ← `develop` ← `feature/* | fix/* | chore/*`
- Conventional commit:
  - `feat(detection): ...`
  - `fix(ui): ...`
  - `chore(deps): ...`
  - `docs(agents): ...`

---

*Son güncelleme: Mart 2026*

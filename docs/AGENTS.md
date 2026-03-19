# AGENTS.md — OpenShield

Bu dosya, AI kod asistanlarının (Claude, Copilot, Cursor vb.) OpenShield üzerinde çalışırken uyması gereken kuralları ve güncel proje mimarisini açıklar.

---

## Proje Kimliği

**OpenShield** — Android için açık kaynak, gizlilik öncelikli SMS spam filtresi.

Temel ilkeler:
- Spam tespiti tamamen cihaz üzerinde çalışır (offline-first)
- SMS içeriği hiçbir zaman kaydedilmez veya iletilmez
- Topluluk katkısı isteğe bağlıdır ve yalnızca kullanıcı onayıyla çalışır
- Mobil veri hiçbir zaman kullanılmaz

---

## Güncel Mimari

```
app/src/main/java/com/openshield/
├── data/
│   ├── db/
│   │   └── SpamDatabase.kt          # Room v3, 5 tablo, MIGRATION_1_2 + MIGRATION_2_3
│   └── repository/
│       ├── SpamRepository.kt        # Kara/beyaz liste, blocked_log, pending_review
│       ├── CommunityRepository.kt   # Topluluk raporu gönderme + community-list sync
│       └── ConsentManager.kt        # communityConsent + lastSyncTime (SharedPreferences)
├── detection/
│   ├── engine/
│   │   └── SpamDetectionEngine.kt   # numara×0.40 + içerik×0.35 + ML×0.25
│   └── rules/
│       └── RuleEngine.kt            # TR/EN kural seti, combo kurallar, OTP whitelist
├── service/
│   └── SmsReceiver.kt               # BroadcastReceiver — analiz, log, pending_review
├── worker/
│   └── WifiSyncManager.kt           # NetworkCallback — Wi-Fi'de flush + 6h sync
├── ui/
│   ├── MainActivity.kt              # 4 sekme: Ana Sayfa, Kara Liste, Beyaz Liste, Geçmiş
│   ├── MainViewModel.kt             # StateFlow: spamNumbers, whitelist, blockedLog, pendingReviews
│   └── SuspiciousReviewDialog.kt    # Şüpheli mesaj kararı — Spam / Değil
└── OpenShieldApp.kt                 # @HiltAndroidApp, wifiSyncManager.register()
```

---

## DB Tabloları (SpamDatabase v3)

| Tablo | İçerik | Notlar |
|---|---|---|
| `spam_numbers` | Kullanıcı kara listesi + topluluk hash'leri | `isUserAdded` ile ayrılır |
| `whitelist` | Kullanıcı beyaz listesi | Düz numara |
| `blocked_log` | Engelleme geçmişi | Gönderici, skor, sebep, zaman |
| `pending_review` | Şüpheli mesajlar | SMS içeriği yok, sadece gönderici+skor+kural |
| `pending_reports` | Wi-Fi yokken biriken topluluk raporları | `voteType`, `retryCount`, `nextRetryAt` |

---

## Sınıflandırma Akışı

```
SMS gelir
  → SmsReceiver.onReceive()
  → SpamDetectionEngine.analyze()
      → numberScore  (kara/beyaz liste, topluluk hash kontrolü)
      → contentScore (RuleEngine)
      → mlScore      (TFLiteClassifier — şu an stub)
      → combinedScore = numara×0.40 + içerik×0.35 + ML×0.25
  → SPAM     (>0.60)  → logBlocked() + reportSpam() [Wi-Fi varsa anında, yoksa kuyruk]
  → SUSPICIOUS (0.40–0.60) → addPendingReview()
  → CLEAN    (<0.40)  → hiçbir şey yapma
```

---

## Topluluk Sistemi

**Backend:** Cloudflare Workers + KV (`cloudflare/worker.js`)
**Base URL:** `https://openshield-api.ensarkaralii.workers.dev`

### Endpoints

| Method | Path | Açıklama |
|---|---|---|
| POST | `/report` | Spam/not_spam oyu gönder |
| GET | `/community-list?since=<ts>` | Delta güncelleme listesi |
| GET | `/admin?key=<ADMIN_KEY>` | Web admin paneli |

### Gönderilen Alanlar (POST /report)

```json
{
  "numberHash": "<sha256>",
  "number": "<düz numara — sadece admin panelinde görünür>",
  "voteType": "spam | not_spam",
  "triggeredRules": ["GAMBLING_BRAND", "COMBO:HAVALE+URL"]
}
```

### Karar Mekanizması

- `spam_votes / (spam_votes + not_spam_votes) > 0.5` → listede
- Minimum oy şartı yok
- Manuel admin override mümkün

### Gönderim Zamanlaması

- Yeni veri + Wi-Fi var → anında gönder
- Yeni veri + Wi-Fi yok → `pending_reports` kuyruğuna yaz
- Wi-Fi'ye bağlanınca → `flushPendingReports()` + `syncCommunityList()`
- 6 saatten önce sync yapılmaz (`ConsentManager.lastSyncTime`)
- Exponential backoff: 30s → 1dk → 2dk → 4dk → 8dk, max 5 deneme

---

## Gizlilik Kuralları (Kritik — İhlal Edilemez)

1. **SMS body loglamak yasak** — `Log.d`, crash payload, analytics event dahil hiçbir yerde
2. **Gerçek numara topluluk sistemine gönderilemez** — sadece SHA-256 hash (`number` alanı yalnızca admin paneli için, community-list response'unda yer almaz)
3. **Mobil veri kullanılamaz** — tüm ağ işlemleri Wi-Fi kontrolünden geçmeli
4. **Topluluk işlemleri consent olmadan başlatılamaz** — `ConsentManager.communityConsent` kontrol edilmeli
5. **SMS içeriği DB'ye yazılamaz** — `pending_review` tablosunda body alanı yok, olmamalı

### Topluluk Gönderiminde İzin Verilen Alanlar

```
numberHash (SHA-256)  ✅
number (düz)          ✅ sadece worker KV'de, community-list'te gönderilmez
triggeredRules        ✅ kural isimleri (örn. GAMBLING_BRAND)
voteType              ✅ "spam" | "not_spam"
SMS içeriği           ❌ asla
cihaz kimliği         ❌ asla
konum                 ❌ asla
```

---

## İzin ve Ağ Politikası

- Zorunlu izinler: `RECEIVE_SMS`, `READ_SMS`, `POST_NOTIFICATIONS`
- Topluluk sistemi için: `INTERNET`, `ACCESS_NETWORK_STATE`
- Temel spam koruması internet olmadan çalışmalıdır
- Mobil veri kullanımı yasak — sadece `TRANSPORT_WIFI` + `NET_CAPABILITY_VALIDATED`

---

## Kod Standartları

- Kotlin kullan, Java ekleme
- `var` yerine `val` tercih et
- `!!` kullanma — `?: return`, `?.let` kullan
- Tüm `suspend` I/O işleri `Dispatchers.IO`'da çalışmalı
- İş mantığını Activity/Fragment içine gömme — ViewModel/Repository katmanında tut
- Hardcoded string kullanma — `strings.xml`'e taşı

---

## Tespit Kuralları

- Banka OTP, fatura, kargo, sigorta hatırlatması → `OTP_WHITELIST` → erken çıkış
- Kumar/bahis sitesi + havale/link kombinasyonu → yüksek skor
- IBAN + dini söylem veya sosyal medya → sahte bağış combo
- Toplu SMS kodu (`B0XX` formatı) + siyasi imza → siyasi spam
- Kural değişikliğinde `RuleEngineTest`'e test eklenmeli

---

## Test Gereksinimleri

- `detection/` altındaki değişikliklerde unit test zorunlu
- Hedef coverage: %80+
- Zorunlu senaryolar:
  - Banka OTP → CLEAN (false positive yok)
  - Açık kumar spam → SPAM (>0.60)
  - Sınır durum mesajları → SUSPICIOUS (0.40–0.60)

---

## Yapılmaması Gerekenler

- SMS body'yi herhangi bir yere yazmak
- Gerçek numarayı topluluk endpoint'ine göndermek (community-list response hariç admin KV)
- `Thread.sleep()` kullanmak — coroutine kullan
- `!!` operatörü kullanmak
- Mobil veri üzerinden ağ isteği yapmak
- Consent kontrolü atlamak
- `abortBroadcast()` çağırmak — Android 4.4+ üzerinde çalışmaz

---

## Git Workflow

```
main      ← kararlı, release
  └── develop ← aktif geliştirme
        ├── feature/xxx
        ├── fix/xxx
        └── chore/xxx
```

Conventional commit:
```
feat(detection): add combo rule for IBAN+social media
fix(community): fix exponential backoff calculation
chore(deps): update Room to 2.7.0
docs(agents): update architecture section
```

---

*Son güncelleme: Mart 2026*

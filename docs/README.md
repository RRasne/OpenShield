# 🛡️ OpenShield — SMS Spam Engelleyici

<p align="center">
  <img src="docs/banner.png" alt="OpenShield Banner" width="600"/>
</p>

<p align="center">
  <a href="https://github.com/RRasne/OpenShield/releases"><img src="https://img.shields.io/github/v/release/RRasne/OpenShield?style=flat-square&color=2563EB" alt="Release"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-green?style=flat-square" alt="License"/></a>
  <img src="https://img.shields.io/badge/android-8.0%2B-brightgreen?style=flat-square" alt="Android 8+"/>
  <img src="https://img.shields.io/badge/tespit-cihaz%20üzerinde-blue?style=flat-square" alt="On-device"/>
  <img src="https://img.shields.io/badge/tracking-sıfır-red?style=flat-square" alt="No tracking"/>
  <img src="https://img.shields.io/badge/reklam-yok-orange?style=flat-square" alt="No ads"/>
</p>

> Android için tamamen cihaz üzerinde çalışan, gizlilik öncelikli SMS spam engelleyici.  
> Hiçbir SMS içeriği cihazınızdan çıkmaz.

---

## ✨ Özellikler

- 🔒 **Çevrimdışı Tespit** — Spam analizi tamamen cihazda, internet gerektirmez
- 🧠 **Katmanlı Analiz** — Numara kontrolü + Türkçe/İngilizce içerik kuralları + ağırlıklı skor
- 🇹🇷 **Türkçe Optimize** — Kumar siteleri, sahte bağış, siyasi toplu mesaj, yatırım dolandırıcılığı
- 🟡 **Akıllı Şüpheli Yönetimi** — Düşük skorlu mesajlar sessizce tutulur; siz karar verirsiniz
- 👥 **Topluluk Listesi** — İsteğe bağlı, anonim SHA-256 hash bildirimi
- 📋 **Kara / Beyaz Liste** — Kendi filtrelerinizi tam kontrol edin
- 🔕 **Sessiz Bildirim** — Spam engellenince ses/titreşim yok, sadece bilgi notu
- 🚫 **Sıfır Analitik** — Reklam yok, izleme kodu yok, üçüncü taraf SDK yok
- ⚡ **Hafif** — Pil ve RAM dostu, arka planda minimal etki

---

## 📱 Nasıl Çalışır?

```
Gelen SMS
    │
    ├─[1]─ Numara Kontrolü
    │       Kara liste · Beyaz liste · Topluluk spam listesi (hash bazlı)
    │
    ├─[2]─ İçerik Analizi (RuleEngine)
    │       Türkçe/İngilizce anahtar kelimeler
    │       URL · IBAN · Kumar markası · Toplu SMS kodu
    │       Combo kurallar (IBAN+dini söylem, havale+link, deneme+bonus vb.)
    │
    └─[3]─ Ağırlıklı Skor (numara×0.40 + içerik×0.35 + ml×0.25)
                │
                ├─ > 0.60  →  🔴 SPAM — sessiz bildirim, geçmişe kaydet
                ├─ 0.40–0.60 →  🟡 ŞÜPHELİ — sessiz tut, uygulamada sor
                └─ < 0.40  →  🟢 TEMİZ — dokunma
```

### Şüpheli Mesaj Akışı

Bir mesaj "şüpheli" sınıflandırıldığında bildirim gösterilmez. Bir sonraki uygulama açılışınızda size gösterilerek "Spam mı, değil mi?" diye sorulur. SMS içeriği hiçbir zaman kaydedilmez; yalnızca gönderici numarası ve skor tutulur.

---

## 🔐 Gizlilik

**OpenShield'in temel felsefesi: Verileriniz size aittir.**

| Konu | Durum |
|---|---|
| SMS içeriği kaydedilir mi? | ❌ Asla |
| Numara düz metin saklanır mı? | Kara/beyaz liste: ✅ (kullanıcı girişi) · Topluluk: ❌ (SHA-256 hash) |
| İnternet bağlantısı kullanılır mı? | Yalnızca topluluk onayı varsa, yalnızca Wi-Fi'de |
| Mobil veri kullanılır mı? | ❌ Asla |
| Reklam / analitik / üçüncü taraf SDK | ❌ Hiçbiri |
| Veri satışı | ❌ Asla |

### İnternet İzni Hakkında

OpenShield `INTERNET` iznine sahiptir. Bu izin **yalnızca iki durumda** kullanılır:

1. **Topluluk spam listesini çekmek** — Wi-Fi'ye bağlandığında, son sync'ten 6+ saat geçmişse, yalnızca onay verildiyse
2. **Anonim spam bildirimi göndermek** — Siz "spam bildir" dediğinizde, yalnızca onay verildiyse, yalnızca Wi-Fi'de

Her iki işlem de onay olmadan **hiçbir zaman gerçekleşmez.** Kaynak kodu açık olduğundan bunu kendiniz doğrulayabilirsiniz.

### Topluluk Bildirimi — Ne Gönderilir?

| Gönderilen | Gönderilmeyen |
|---|---|
| Numaranın SHA-256 hash'i | Numaranın kendisi |
| Tetiklenen kural isimleri (örn. `GAMBLING_BRAND`) | SMS içeriği |
| Spam skoru (örn. `0.87`) | Cihaz kimliği |
| Spam kategorisi (örn. `GAMBLING`) | Konum veya IP |

Hash tek yönlüdür — orijinal numara geri elde **edilemez.**

Bir numara topluluk listesine girmek için **en az 5 farklı kullanıcıdan** bildirim almalıdır.

---

## 🚀 Kurulum

### Play Store
*(Yakında)*

### Manuel APK
[Releases](https://github.com/RRasne/OpenShield/releases) sayfasından en son APK'yı indirin.

### Kaynak Koddan Derleme
```bash
git clone https://github.com/RRasne/OpenShield.git
cd OpenShield
./gradlew assembleRelease
```

---

## 🏗️ Mimari

```
app/src/main/java/com/openshield/
├── data/
│   ├── db/                          # Room — SpamDatabase (v3, 5 tablo)
│   ├── repository/                  # SpamNumberRepository — tek erişim noktası
│   ├── BundledSpamImporter.kt       # APK ile gelen CSV → Room
│   └── SpamReporter.kt              # Anonim topluluk raporu gönderici
├── detection/
│   ├── engine/                      # SpamDetectionEngine — skor birleştirme
│   └── rules/                       # RuleEngine — TR/EN kural seti
├── service/
│   └── SmsReceiver.kt               # SMS alımı, analiz, sessiz bildirim
├── worker/
│   ├── CommunityUpdateWorker.kt     # Delta sync (Wi-Fi, consent gerekli)
│   └── WifiSyncManager.kt           # NetworkCallback — Wi-Fi'ye bağlanınca tetikler
├── util/
│   └── ConsentManager.kt            # Onay ve sync zamanı yönetimi
├── ui/
│   ├── MainActivity.kt              # 5 sekme (Ana Sayfa, Kara/Beyaz Liste, Geçmiş, Ayarlar)
│   ├── MainViewModel.kt             # StateFlow tabanlı UI state
│   ├── MessageHistoryScreen.kt      # SMS geçmişi, gönderici gruplu, işaretleme
│   └── SuspiciousReviewDialog.kt    # Şüpheli mesaj karar dialogu
└── OpenShieldApp.kt                 # WifiSyncManager'ı başlatır
```

### DB Tabloları

| Tablo | İçerik |
|---|---|
| `spam_numbers` | Kullanıcının kara listesi |
| `whitelist` | Kullanıcının beyaz listesi |
| `blocked_log` | Engelleme geçmişi |
| `community_reports` | Topluluk raporları (SHA-256 hash) |
| `pending_review` | Şüpheli mesajlar — karar bekleniyor |

**Teknolojiler:** Jetpack Compose · Room · Hilt · Kotlin Coroutines · WorkManager · Cloudflare Workers

---

## 👥 Topluluk Spam Listesi

Topluluk listesi iki katmandan oluşur:

- **Bundled liste** — Her APK sürümüyle gelen, önceden onaylanmış spam hash'leri (`assets/bundled_spam.csv`)
- **Canlı liste** — Kullanıcı bildirimleriyle büyüyen, Cloudflare KV üzerinde tutulan hash veritabanı

Uygulama Wi-Fi'ye bağlandığında ve onay varsa delta endpoint'ten (`?since=<timestamp>`) yalnızca yeni eklenenler çekilir. Tüm veriler SHA-256 hash formatındadır.

Cloudflare Worker kaynak kodu: [`cloudflare/worker.js`](cloudflare/worker.js)

---

## 🤝 Katkı

Her türlü katkıya açığız:

- 🇹🇷 Türkçe spam anahtar kelime listesi genişletme
- 🌍 Başka dil desteği
- 🐛 Hata bildirimi
- 📊 Anonimize edilmiş spam SMS dataset katkısı

Lütfen önce [AGENTS.md](AGENTS.md) dosyasını okuyun, ardından PR açın.

---

## 📄 Lisans

[GNU General Public License v3.0](LICENSE)

---

<p align="center">
  Gizliliğiniz bir özellik değil, bir haktır.
</p>

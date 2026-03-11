# 🛡️ OpenShield — SMS Spam Engelleyici

<p align="center">
  <img src="docs/banner.png" alt="OpenShield Banner" width="600"/>
</p>

<p align="center">
  <a href="https://github.com/RRasne/OpenShield/releases"><img src="https://img.shields.io/github/v/release/RRasne/OpenShield?style=flat-square&color=2563EB" alt="Release"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-green?style=flat-square" alt="License"/></a>
  <img src="https://img.shields.io/badge/android-8.0%2B-brightgreen?style=flat-square" alt="Android 8+"/>
  <img src="https://img.shields.io/badge/spam%20tespiti-cihaz%20üzerinde-blue?style=flat-square" alt="On-device"/>
  <img src="https://img.shields.io/badge/tracking-sıfır-red?style=flat-square" alt="No tracking"/>
  <img src="https://img.shields.io/badge/reklam-yok-orange?style=flat-square" alt="No ads"/>
</p>

> Android için tamamen cihaz üzerinde çalışan, gizlilik öncelikli SMS spam engelleyici.  
> Hiçbir SMS içeriği cihazınızdan çıkmaz.

---

## ✨ Özellikler

- 🔒 **Çevrimdışı Tespit** — Spam analizi tamamen cihazda yapılır
- 🧠 **Katmanlı Analiz** — Numara kontrolü + Türkçe/İngilizce içerik kuralları + ağırlıklı skor
- 🇹🇷 **Türkçe Optimize** — Kumar siteleri, sahte bağış, siyasi toplu mesaj, yatırım dolandırıcılığı
- 👥 **Topluluk Listesi** — İsteğe bağlı, anonim hash bildirimi ile topluluk spam veritabanına katkı
- 📋 **Kara / Beyaz Liste** — Kendi filtrelerinizi tam kontrol edin
- 🚫 **Reklam Yok** — Sıfır analitik, sıfır izleme kodu, sıfır üçüncü taraf SDK
- ⚡ **Hafif** — Pil ve RAM dostu, arka planda minimal etki

---

## 📱 Nasıl Çalışır?

```
Gelen SMS
    │
    ├─[1]─ Numara Kontrolü
    │       Kara liste · Beyaz liste · Topluluk spam listesi
    │
    ├─[2]─ İçerik Analizi (RuleEngine)
    │       Türkçe/İngilizce anahtar kelimeler
    │       URL · IBAN · Kumar markası · Toplu SMS kodu
    │       Combo kurallar (IBAN+dini söylem, havale+link vb.)
    │
    └─[3]─ Ağırlıklı Skor Birleştirme
                │
                ▼
         > 0.60  →  🔴 SPAM engellendi, bildirim
         0.40–0.60 →  🟡 ŞÜPHELİ, uyarı
         < 0.40  →  🟢 TEMİZ, dokunulmaz
```

---

## 🔐 Gizlilik

**OpenShield'in temel felsefesi: Verileriniz size aittir.**

| Konu | Durum |
|---|---|
| SMS içeriği kaydedilir mi? | ❌ Asla, hiçbir şekilde |
| Numara düz metin saklanır mı? | ❌ Yalnızca SHA-256 hash |
| İnternet izni var mı? | ✅ Var — yalnızca aşağıdaki amaçla |
| Reklam / analitik / üçüncü taraf SDK | ❌ Hiçbiri |
| Veri satışı | ❌ Asla |

### İnternet İzni Hakkında

OpenShield, `INTERNET` iznine sahiptir. Bu izin **yalnızca iki amaç için** kullanılır:

1. **Topluluk spam listesini çekmek** — Haftada bir, yalnızca Wi-Fi'de, arka planda
2. **Anonim spam bildirimi göndermek** — Yalnızca siz "spam bildir" dediğinizde, yalnızca onay verirseniz

Bu iki işlem dışında uygulama **hiçbir ağ bağlantısı kurmaz.** Kaynak kodu açık olduğundan bunu kendiniz doğrulayabilirsiniz.

### Topluluk Bildirimi — Ne Gönderilir, Ne Gönderilmez?

| Gönderilen | Gönderilmeyen |
|---|---|
| Numaranın SHA-256 hash'i | Numaranın kendisi |
| Tetiklenen kural isimleri (örn. `GAMBLING_BRAND`) | SMS içeriği |
| Spam skoru (örn. `0.87`) | Cihaz kimliği |
| Spam kategorisi (örn. `GAMBLING`) | Konum, IP veya kimlik |

Hash tek yönlü bir dönüşümdür — orijinal numara **geri elde edilemez.**

### Eşik Sistemi

Bir numara topluluk listesine girmesi için **en az 5 farklı kullanıcıdan** bildirim alması gerekir. Tek kişinin bildirimi listeye girmez, yalnızca cihazında kaydedilir.

### Sorumluluk Sınırı

OpenShield açık kaynaklı bir topluluk projesidir. Geliştiriciler, uygulamanın üçüncü taraf sistemlerde (Cloudflare vb.) işlenen verilerden doğabilecek durumlar için sorumluluk kabul etmez. Uygulamayı kullanmadan önce [Gizlilik Politikası](PRIVACY.md)'nı okumanızı öneririz. Kaynak kodunu inceleyerek bu açıklamaları kendiniz doğrulayabilirsiniz.

[Gizlilik Politikası →](PRIVACY.md)

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
│   ├── db/                     # Room — SpamDatabase, tüm DAO'lar
│   ├── repository/             # SpamNumberRepository
│   ├── BundledSpamImporter.kt  # assets CSV → Room import
│   └── SpamReporter.kt         # Anonim bildirim gönderici
├── detection/
│   ├── engine/                 # SpamDetectionEngine — skor birleştirme
│   └── rules/                  # RuleEngine — Türkçe/İngilizce kural seti
├── service/                    # SmsReceiver — BroadcastReceiver
├── worker/                     # CommunityUpdateWorker — haftada bir güncelleme
├── ui/
│   ├── MainActivity.kt         # Ana ekran (Compose)
│   └── MainViewModel.kt
└── OpenShieldApp.kt
```

**Teknolojiler:** Jetpack Compose · Room · Hilt · Kotlin Coroutines · WorkManager · Cloudflare Workers

---

## 👥 Topluluk Spam Listesi

Topluluk listesi iki katmandan oluşur:

- **Bundled liste** — Her APK sürümüyle birlikte gelen, onaylanmış spam numaraları
- **Canlı liste** — Kullanıcı bildirimleriyle büyüyen, Cloudflare üzerinde tutulan hash veritabanı

Tüm veriler SHA-256 ile hash'lenir. Cloudflare Worker kaynak kodu bu repoda [`cloudflare/worker.js`](cloudflare/worker.js) olarak mevcuttur.

---

## 🤝 Katkı

Her türlü katkıya açığız:

- 🇹🇷 Türkçe spam anahtar kelime listesi genişletme
- 🌍 Başka dil desteği (regex + kelime listeleri)
- 🐛 Hata bildirimi
- 📊 Spam SMS dataset katkısı (anonimize edilmiş)

Lütfen önce [AGENTS.md](AGENTS.md) dosyasını okuyun, ardından PR açın.

---

## 📄 Lisans

[GNU General Public License v3.0](LICENSE)

---

<p align="center">
  Gizliliğiniz bir özellik değil, bir haktır.
</p>
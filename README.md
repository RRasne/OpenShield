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

- 🔒 **Çevrimdışı Tespit** — Spam analizi tamamen cihazda yapılır, internet gerekmez
- 🧠 **Katmanlı Analiz** — Numara kontrolü + Türkçe/İngilizce içerik kuralları + skor birleştirme
- 🇹🇷 **Türkçe Optimize** — Kumar siteleri, sahte bağış, siyasi toplu mesaj, yatırım dolandırıcılığı
- 👥 **Topluluk Listesi** — Kullanıcı onayıyla anonim hash bildirimi; listeye katkı sağlanır
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
    │       URL · IBAN · Kumar markası · SMSRET kodu
    │       Combo kurallar (IBAN+dini söylem, havale+link vb.)
    │
    └─[3]─ Ağırlıklı Skor Birleştirme
                │
                ▼
         > 0.60  →  🔴 SPAM engellenid, bildirim
         0.40–0.60 →  🟡 ŞÜPHELİ, uyarı
         < 0.40  →  🟢 TEMİZ, dokunulmaz
```

---

## 🔐 Gizlilik

OpenShield'in temel felsefesi: **Verileriniz cihazınızda kalır.**

| Konu | Durum |
|---|---|
| SMS içeriği kaydedilir mi? | ❌ Asla |
| Numara düz metin saklanır mı? | ❌ SHA-256 hash olarak saklanır |
| İnternet kullanılır mı? | Yalnızca kullanıcı onayıyla anonim bildirim için |
| Reklam / analitik / SDK | ❌ Hiçbiri |
| Veri satışı | ❌ Asla |

### Topluluk Katkısı (İsteğe Bağlı)

İlk kurulumda sorulur. "Evet" seçerseniz:
- Spam numaranın **SHA-256 hash'i** (orijinal numara değil) GitHub'a gönderilir
- SMS içeriği, gerçek numara, konum veya kimlik **asla gönderilmez**
- Yalnızca internete bağlandığınızda arka planda çalışır
- Ayarlar'dan her zaman kapatılabilir

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
│   ├── db/             # Room — SpamDatabase, tüm DAO'lar
│   └── repository/     # SpamRepository
├── detection/
│   ├── engine/         # SpamDetectionEngine — skor birleştirme
│   └── rules/          # RuleEngine — Türkçe/İngilizce kural seti
├── service/            # SmsReceiver — BroadcastReceiver
├── worker/             # CommunityReportWorker — WorkManager
├── ui/
│   ├── MainActivity.kt         # Ana ekran (Compose)
│   ├── OnboardingActivity.kt   # İlk kurulum akışı
│   └── MainViewModel.kt
└── OpenShieldApp.kt
```

**Teknolojiler:** Jetpack Compose · Room · Hilt · Kotlin Coroutines · WorkManager

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

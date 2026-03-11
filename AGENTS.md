# AGENTS.md - OpenShield

Bu dosya, AI kod asistanlarının (Claude, Copilot, Cursor vb.) OpenShield üzerinde çalışırken uyması gereken kuralları özetler.

---

## Proje Kimliği

**OpenShield**: Android için gizlilik öncelikli SMS spam filtresi.

Temel ilkeler:
- Tespit motoru cihaz üzerinde çalışır (offline-first).
- SMS içeriği ve gerçek telefon numarası dış servislere gönderilmez.
- Topluluk katkısı **isteğe bağlıdır** ve yalnızca kullanıcı onayı ile çalışır.

---

## Mimari Özeti

- `detection/engine`: Sınıflandırma akışı (`CLEAN`, `SUSPICIOUS`, `SPAM`)
- `detection/rules`: Kural tabanlı skorlayıcı (TR/EN kalıplar, URL, IBAN, kampanya vb.)
- `data/db`: Room tabloları (spam, whitelist, blocked log)
- `service/SmsReceiver`: SMS alımı, analiz, sessiz bildirim
- `worker/`: WorkManager işleri
  - `CommunityReportWorker`: consent ve planlama
  - `SpamReportUploadWorker`: ağ varken anonim topluluk raporu gönderimi
- `ui/OnboardingActivity`: ilk açılış bilgilendirme + izin + topluluk katkısı seçimi

---

## Gizlilik Kuralları (Kritik)

1. SMS body loglamak yasak (`Log.d`, crash payload, analytics event içinde dahil).
2. Topluluk gönderiminde yalnızca anonim veri kullanılabilir.
3. Gönderilecek alanlar:
   - `number_hash` (SHA-256)
   - `rules`
   - `score`
   - `category`
4. Asla gönderilmeyecek alanlar:
   - düz telefon numarası
   - SMS metni
   - kişi kimliği / rehber verisi / konum
5. Topluluk paylaşımı **opt-in** olmalı:
   - Kullanıcı onboarding/ayarlar ekranında açıkça izin verirse aktif.
   - İzin yoksa sadece cihaz içi çalışma.

---

## İzin ve Ağ Politikası

- Gerekli izinler: `RECEIVE_SMS`, `READ_SMS`, (Android 13+) `POST_NOTIFICATIONS`
- `INTERNET` izni yalnızca topluluk katkısı için kullanılabilir.
- Uygulamanın temel spam koruması internet olmadan çalışmalıdır.

---

## Kod Standartları

- Kotlin kullan, Java ekleme.
- `var` yerine mümkün olduğunca `val`.
- `!!` kaçın; `?: return`, `?.let` tercih et.
- `suspend` I/O işleri `Dispatchers.IO` üzerinde çalışmalı.
- İş mantığını `Activity` içine gömme; repository/engine/viewmodel katmanında tut.

---

## Tespit Kuralları İçin Notlar

- Banka OTP, fatura, gerçek servis hatırlatma mesajlarında false-positive düşük tutulmalı.
- Reklam/kampanya/sahte yönlendirme (özellikle kısa link + satış dili + bulk kod kombinasyonu) daha agresif puanlanmalı.
- Kural değişikliğinde mutlaka unit test eklenmeli.

---

## Test Gereksinimi

- `detection/` altındaki değişikliklerde unit test zorunlu.
- En az şu senaryolar test edilmeli:
  - meşru banka/fatura mesajı temiz kalır
  - bariz kampanya-spam mesajı spam olur
  - sınır durum mesajları `SUSPICIOUS` bandında kalır

---

## Yapılmaması Gerekenler

- SMS içeriğini veya gerçek numarayı uzak servise yollama.
- SharedPreferences/DB içinde SMS body biriktirme.
- `Thread.sleep()` kullanma (coroutine kullan).
- Destructive git komutları (`reset --hard` vb.) izinsiz çalıştırma.

---

## Git ve Commit

- Branch akışı: `main` <- `develop` <- `feature/* | fix/* | chore/*`
- Conventional commit kullan:
  - `feat(detection): ...`
  - `fix(ui): ...`
  - `chore(deps): ...`
  - `docs(agents): ...`

---

Son güncelleme: 2026-03-07

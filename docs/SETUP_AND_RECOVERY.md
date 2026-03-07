# OpenShield Sifirdan Kurulum ve Kurtarma Rehberi

Bu dokuman yeni bir bilgisayarda projeyi sifirdan kurmak, calistirmak, derlemek ve Cloudflare tarafini test etmek icin hazirlandi.

## 1. Hizli Referans

- Proje koku: `C:\Users\Ensar\openshield`
- Cloudflare stats: [https://openshield-community.ensarkaralii.workers.dev/stats](https://openshield-community.ensarkaralii.workers.dev/stats)
- Cloudflare community csv: [https://openshield-community.ensarkaralii.workers.dev/community.csv](https://openshield-community.ensarkaralii.workers.dev/community.csv)
- Cloudflare report endpoint (POST): [https://openshield-community.ensarkaralii.workers.dev/report](https://openshield-community.ensarkaralii.workers.dev/report)
- Cloudflare worker kodu: `cloudflare/worker.js`
- Wrangler config: `cloudflare/wrangler.toml`
- Profil helper script: `setup_profile.ps1`

## 2. Gereksinimler

- Windows 10/11
- Android Studio (guncel)
- JDK 21
- Android SDK (compileSdk ile uyumlu)
- Git

## 3. Projeyi Sifirdan Kurma

```powershell
git clone https://github.com/RRasne/OpenShield.git
cd OpenShield
```

Android Studio ile:
1. `Open` -> repo klasorunu sec.
2. Gradle sync bekle.
3. Eksik SDK paketlerini IDE uzerinden kur.

## 4. Derleme ve Test Komutlari

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
.\gradlew.bat :app:assembleRelease --no-daemon
```

## 5. setup_profile.ps1 Kullanimi

`setup_profile.ps1` PowerShell profiline Git yardimci komutlari ekler (`gpp`, `gppclear`, `gppversion`).

Calistirma:

```powershell
powershell -ExecutionPolicy Bypass -File .\setup_profile.ps1
```

Yeni terminal acip dogrula:

```powershell
Get-Command gpp
gpp "chore: quick update"
gppversion -version "1.2.3" -msg "chore(release): v1.2.3"
```

Not:
- `gppclear` gecmisi temizleyen guclu bir komuttur, dikkatli kullan.
- `gppversion` `app/build.gradle.kts` dosyasinda `versionName/versionCode` gunceller.

## 6. Cloudflare Endpoint Testleri

Stats:

```powershell
Invoke-WebRequest "https://openshield-community.ensarkaralii.workers.dev/stats" | Select-Object -ExpandProperty Content
```

Community CSV:

```powershell
Invoke-WebRequest "https://openshield-community.ensarkaralii.workers.dev/community.csv" | Select-Object -ExpandProperty Content
```

Report POST ornegi:

```powershell
$body='{"number_hash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","rules":["test_rule"],"score":0.95,"category":"PHISHING"}'
Invoke-WebRequest "https://openshield-community.ensarkaralii.workers.dev/report" -Method POST -ContentType "application/json" -Body $body | Select-Object -ExpandProperty Content
```

## 7. Yerel Dosya Kaybi Sonrasi Toparlama

1. Repo tekrar klonla.
2. Gradle sync yap.
3. Asagidaki sirayla dogrula:
   - `:app:compileDebugKotlin`
   - `:app:testDebugUnitTest`
   - `:app:assembleDebug`
4. Cloudflare endpointlerini kontrol et (`/stats`, `/community.csv`).
5. Cihaz/emulator ile onboarding + izin + ayarlar akisini test et.

## 8. Cikti Konumlari

- Debug APK: `app\build\outputs\apk\debug\`
- Release APK: `app\build\outputs\apk\release\`
- Unit test raporu: `app\build\reports\tests\`

## 9. Guvenlik Hatirlatmasi

- Topluluk gonderimi opsiyoneldir (kullanici onayi ile).
- SMS metni veya duz numara gonderilmez.
- Uygulamanin temel spam korumasi internet bagimsiz calisir.

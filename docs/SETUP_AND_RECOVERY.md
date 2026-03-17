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

## 5. setup_profile.ps1 Sifirdan Kurulumu (Geri Yukleme)

Projeyi format sonrasi veya baska bir bilgisayara tasidiginizda asagidaki adimlari sirayla uygula.

> **ONEMLI:** Windows'ta `Windows PowerShell` (eski) ve `PowerShell 7+` (yeni, `pwsh`) olmak uzere iki farkli surum vardir. Adim 4 olmadan `gpp` PowerShell 7'de tanimlanmaz.

**Adim 1 — Script calistirma iznini ver:**
```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned -Force
```

**Adim 2 — Proje dizinine gec ve scripti calistir:**
```powershell
cd C:\Users\Ensar\Desktop\OpenShield
powershell -ExecutionPolicy Bypass -File ".\setup_profile.ps1"
```

**Adim 3 — PowerShell 7 icin profili kopyala** *(bu adimi atlama!)*:
```powershell
$newProfileDir = "$env:USERPROFILE\Documents\PowerShell"
New-Item -ItemType Directory -Force -Path $newProfileDir | Out-Null
$src = Get-Content "$env:USERPROFILE\Documents\WindowsPowerShell\Microsoft.PowerShell_profile.ps1" -Raw
Set-Content "$newProfileDir\Microsoft.PowerShell_profile.ps1" $src -Encoding UTF8
Write-Host "Hazir!" -ForegroundColor Green
```

**Adim 4 — Terminali tamamen kapat, yeni bir PowerShell 7 ac.**

**Adim 5 — Dogrula:**
```powershell
Get-Command gpp
```
`gpp` goruntuleniyor ise kurulum tamamdir.

---

### Komut ozeti

| Komut | Aciklama |
|---|---|
| `gpp "mesaj"` | Release build al, basarili ise commit + push yap |
| `gpp -BuildOnly` | Sadece release build al, git'e dokunma |
| `gpp "mesaj" -SkipBuild` | Build almadan direkt commit + push yap |
| `gppclear "mesaj"` | Gecmisi sifirla, zorla push yap (dikkat — geri alinmaz!) |
| `gppversion -version "1.2.3"` | Surumu artir, commit + tag + push yap |

---

### Sikca karsilasilan sorunlar

| Hata | Cozum |
|---|---|
| `gpp is not recognized` | Adim 3'u (PowerShell 7 profil kopyalama) yapmadiniz |
| `running scripts is disabled` | Adim 1'i calistirin: `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned -Force` |
| `unable to auto-detect email` | `git config --global user.email "mail@example.com"` calistirin |
| Profil acilirken kirmizi `WinGet` hatasi | Onemsiz, `gpp` calismasini etkilemez, goz ardi edin |

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

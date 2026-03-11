New-Item -ItemType Directory -Force -Path "$env:USERPROFILE\Documents\WindowsPowerShell" | Out-Null

@'
function gpp {
    param($msg = "update $(Get-Date -Format 'yyyy-MM-dd HH:mm')")
    git add .
    git commit -m $msg
    git push
}

function gppclear {
    param($msg = "update $(Get-Date -Format 'yyyy-MM-dd HH:mm')")
    git checkout --orphan fresh
    git add .
    git commit -m $msg
    git branch -D main
    git branch -m main
    git push origin main --force
    Write-Host "Gecmis temizlendi!" -ForegroundColor Green
}

function gppversion {
    param(
        [Parameter(Mandatory=$true)][string]$version,
        [string]$msg = ""
    )

    $gradlePath = "app/build.gradle.kts"

    if (-not (Test-Path $gradlePath)) {
        Write-Host "HATA: $gradlePath bulunamadi!" -ForegroundColor Red
        return
    }

    $content = Get-Content $gradlePath -Raw

    # Mevcut versionName ve versionCode'u oku
    $oldVersionName = if ($content -match 'versionName\s*=\s*"([^"]+)"') { $matches[1] } else { $null }
    $oldVersionCode = if ($content -match 'versionCode\s*=\s*(\d+)') { [int]$matches[1] } else { $null }

    if ($null -eq $oldVersionName -or $null -eq $oldVersionCode) {
        Write-Host "HATA: versionName veya versionCode okunamadi!" -ForegroundColor Red
        return
    }

    $newVersionCode = $oldVersionCode + 1
    $commitMsg = if ($msg -ne "") { $msg } else { "chore(release): v$version (build $newVersionCode)" }

    Write-Host "versionName  : $oldVersionName  ->  $version" -ForegroundColor Cyan
    Write-Host "versionCode  : $oldVersionCode  ->  $newVersionCode" -ForegroundColor Cyan

    # Dosyayı güncelle
    $newContent = $content `
        -replace '(versionName\s*=\s*")[^"]+(")', "`${1}$version`$2" `
        -replace '(versionCode\s*=\s*)\d+', "`${1}$newVersionCode"

    try {
        Set-Content $gradlePath $newContent -NoNewline -ErrorAction Stop

        git add $gradlePath
        if ($LASTEXITCODE -ne 0) { throw "git add basarisiz" }

        git commit -m $commitMsg
        if ($LASTEXITCODE -ne 0) { throw "git commit basarisiz" }

        git tag "v$version"
        if ($LASTEXITCODE -ne 0) { throw "git tag basarisiz" }

        git push
        if ($LASTEXITCODE -ne 0) { throw "git push basarisiz" }

        git push origin "v$version"
        if ($LASTEXITCODE -ne 0) { throw "git push tag basarisiz" }

        Write-Host "v$version basariyla yayinlandi!" -ForegroundColor Green

    } catch {
        Write-Host "HATA: $_" -ForegroundColor Red
        Write-Host "Rollback yapiliyor..." -ForegroundColor Yellow

        Set-Content $gradlePath $content -NoNewline

        # Eğer commit yapıldıysa geri al
        $lastCommit = git log --oneline -1 2>$null
        if ($lastCommit -match [regex]::Escape($commitMsg)) {
            git reset HEAD~1 | Out-Null
            git checkout -- $gradlePath | Out-Null
        }

        # Eğer tag oluştuysa sil
        $tagExists = git tag -l "v$version"
        if ($tagExists) {
            git tag -d "v$version" | Out-Null
        }

        Write-Host "Rollback tamamlandi. Dosya eski haline dondu." -ForegroundColor Yellow
    }
}
'@ | Set-Content "$env:USERPROFILE\Documents\WindowsPowerShell\Microsoft.PowerShell_profile.ps1" -Encoding UTF8

. $PROFILE
Write-Host "Hazir!" -ForegroundColor Green

<#
README
======

Bu script ne işe yarar?
-----------------------
- PowerShell profil dosyasına Git yardımcı fonksiyonları ekler.
- gpp       : git add + commit + push işlemlerini tek komutla yapar.
- gppclear  : geçmiş commit'leri temizleyip yeni bir main branch oluşturur.
- gppversion: Android Gradle dosyasındaki versionName ve versionCode'u günceller, commit/tag/push işlemlerini yapar.

İlk kez kurulum (sıfırdan ayarlama)
-----------------------------------
1. Git kimlik bilgilerini ayarla:
   git config --global user.name "Your Name"
   git config --global user.email "you@example.com"

2. PowerShell profil dosyasını oluştur:
   New-Item -ItemType File -Force -Path $PROFILE

3. Bu scriptin içeriğini profil dosyasına kopyala:
   notepad $PROFILE

4. Dosyayı kaydet ve PowerShell’de yükle:
   . $PROFILE

5. Artık aşağıdaki komutları kullanabilirsin:
   gpp "mesaj"
   gppclear "mesaj"
   gppversion -version "1.2.3" -msg "release notu"

Notlar
------
- Bu scriptteki e‑posta ve isim örnektir, kendi bilgilerinizi girmeniz gerekir.
- Eğer commit geçmişini temizlemek istemiyorsanız `gppclear` komutunu dikkatli kullanın.
- Android projelerinde `gppversion` fonksiyonu yalnızca `app/build.gradle.kts` dosyası varsa çalışır.
#>
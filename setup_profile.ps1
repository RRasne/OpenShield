# OpenShield — gpp helper kurulum scripti
# Her iki PowerShell surumu icin (PS5 + PS7) profilleri gunceller.

# Execution policy kontrolu
$policy = Get-ExecutionPolicy -Scope CurrentUser
if ($policy -eq 'Restricted' -or $policy -eq 'Undefined') {
    Set-ExecutionPolicy -Scope CurrentUser RemoteSigned -Force
    Write-Host "Execution policy ayarlandi: RemoteSigned" -ForegroundColor Yellow
}

$managedBlock = @'
# >>> OpenShield gpp helpers >>>
function gpp {
    param(
        [string]$msg = "update $(Get-Date -Format 'yyyy-MM-dd HH:mm')",
        [switch]$BuildOnly,
        [switch]$SkipBuild
    )

    if (-not $SkipBuild -and (Test-Path ".\gradlew.bat")) {
        if (-not $env:JAVA_HOME -and (Test-Path "C:\Program Files\Android\Android Studio\jbr")) {
            $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
            $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
        }

        if (-not $env:ANDROID_HOME -and (Test-Path "$env:LOCALAPPDATA\Android\Sdk")) {
            $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
        }

        if (-not $env:ANDROID_SDK_ROOT -and $env:ANDROID_HOME) {
            $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
        }

        Write-Host "Release build aliniyor..." -ForegroundColor Cyan
        & .\gradlew.bat :app:assembleRelease --no-daemon

        if ($LASTEXITCODE -ne 0) {
            Write-Host "HATA: Build basarisiz. Git islemleri durduruldu." -ForegroundColor Red
            return
        }

        Write-Host "Build tamamlandi." -ForegroundColor Green

        if ($BuildOnly) { return }
    } elseif ($BuildOnly) {
        Write-Host "HATA: Bu klasorde gradlew.bat bulunamadi." -ForegroundColor Red
        return
    }

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

    $newContent = $content `
        -replace '(versionName\s*=\s*")[^"]+(")', "`${1}$version`$2" `
        -replace '(versionCode\s*=\s*)\d+', "`${1}$newVersionCode"

    try {
        Set-Content $gradlePath $newContent -NoNewline -ErrorAction Stop
        git add $gradlePath
        git commit -m $commitMsg
        git tag "v$version"
        git push
        git push origin "v$version"
        Write-Host "v$version basariyla yayinlandi!" -ForegroundColor Green
    } catch {
        Write-Host "HATA: $_" -ForegroundColor Red
        Write-Host "Rollback yapiliyor..." -ForegroundColor Yellow
        Set-Content $gradlePath $content -NoNewline
        $lastCommit = git log --oneline -1 2>$null
        if ($lastCommit -match [regex]::Escape($commitMsg)) {
            git reset HEAD~1 | Out-Null
            git checkout -- $gradlePath | Out-Null
        }
        $tagExists = git tag -l "v$version"
        if ($tagExists) { git tag -d "v$version" | Out-Null }
        Write-Host "Rollback tamamlandi." -ForegroundColor Yellow
    }
}
# <<< OpenShield gpp helpers <<<
'@

$startMarker = "# >>> OpenShield gpp helpers >>>"
$endMarker   = "# <<< OpenShield gpp helpers <<<"

function Update-Profile ($profilePath) {
    $dir = Split-Path -Parent $profilePath
    New-Item -ItemType Directory -Force -Path $dir | Out-Null

    $existing = if (Test-Path $profilePath) { Get-Content $profilePath -Raw } else { "" }

    if ($existing -match [regex]::Escape($startMarker)) {
        $updated = [regex]::Replace(
            $existing,
            [regex]::Escape($startMarker) + ".*?" + [regex]::Escape($endMarker),
            [System.Text.RegularExpressions.MatchEvaluator]{ param($m) $managedBlock },
            [System.Text.RegularExpressions.RegexOptions]::Singleline
        )
    } elseif ([string]::IsNullOrWhiteSpace($existing)) {
        $updated = $managedBlock
    } else {
        $updated = $existing.TrimEnd() + "`r`n`r`n" + $managedBlock
    }

    Set-Content $profilePath $updated -Encoding UTF8
    Write-Host "  -> Guncellendi: $profilePath" -ForegroundColor Green
}

# PS5 (WindowsPowerShell) profili
$ps5Profile = "$env:USERPROFILE\Documents\WindowsPowerShell\Microsoft.PowerShell_profile.ps1"
# PS7 (PowerShell) profili
$ps7Profile = "$env:USERPROFILE\Documents\PowerShell\Microsoft.PowerShell_profile.ps1"

Write-Host "Profiller guncelleniyor..." -ForegroundColor Cyan
Update-Profile $ps5Profile
Update-Profile $ps7Profile

# Mevcut oturuma hemen yukle
. $ps5Profile
if ($PSVersionTable.PSVersion.Major -ge 7) { . $ps7Profile }

Write-Host ""
Write-Host "Kurulum tamamlandi! 'gpp', 'gppclear', 'gppversion' hazir." -ForegroundColor Green
Write-Host "Yeni terminal acarak veya su an kullanmak icin asagidaki komutu calistir:" -ForegroundColor Yellow
Write-Host "  . `$PROFILE" -ForegroundColor White

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$pub = Join-Path $root "web\public"
New-Item -ItemType Directory -Force -Path $pub | Out-Null

Copy-Item (Join-Path $root "app\src\main\res\mipmap-xxxhdpi\ic_launcher.png") (Join-Path $pub "logo.png") -Force
if (Test-Path (Join-Path $root "qris.png")) {
    Copy-Item (Join-Path $root "qris.png") (Join-Path $pub "qris.png") -Force
}

$apkSrc = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apkSrc)) {
    throw "Release APK not found at $apkSrc. Run .\gradlew.bat assembleRelease first."
}
Copy-Item $apkSrc (Join-Path $pub "Rezona-release.apk") -Force

$apkInfo = Get-Item (Join-Path $pub "Rezona-release.apk")
$sizeMb = "{0:N2} MB" -f ($apkInfo.Length / 1MB)
$dateStr = $apkInfo.LastWriteTime.ToString("MMM d, yyyy", [System.Globalization.CultureInfo]::InvariantCulture)
@{
    size = $sizeMb
    updated = $dateStr
    version = "1.0.1"
    hasQrisFile = Test-Path (Join-Path $pub "qris.png")
} | ConvertTo-Json | Set-Content (Join-Path $pub "release-meta.json") -Encoding UTF8

Write-Host "Synced web/public -> Rezona-release.apk ($sizeMb, $dateStr)"

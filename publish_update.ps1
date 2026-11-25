param(
    [string]$VersionName = "1.0.3",
    [int]$VersionCode = 3,
    [string]$Title = "Update Rezona V1.0.3",
    [string[]]$Add = @("Spatial Prism & Vinyl Studio UI Redesign", "Giant 120fps Spinning Vinyl Record in Now Playing"),
    [string[]]$Fix = @("Dynamic Island anti-ketimpa", "Settings background bleed fix"),
    [string[]]$Improved = @("Smart Camera Cutout placement", "Play count & total listening time"),
    [string]$Note = "Install ulang di : https://rezona-music-web.vercel.app",
    [switch]$Push
)

$stamp = (Get-Date).ToString("yyyyMMddHHmm")
$changelogLines = New-Object System.Collections.Generic.List[string]
if ($Add.Count -gt 0) {
    $changelogLines.Add("1. Add :")
    foreach ($item in $Add) { $changelogLines.Add("- $item") }
    $changelogLines.Add("")
}
if ($Fix.Count -gt 0) {
    $changelogLines.Add("2. Fix :")
    foreach ($item in $Fix) { $changelogLines.Add("- $item") }
    $changelogLines.Add("")
}
if ($Improved.Count -gt 0) {
    $changelogLines.Add("3. Improved :")
    foreach ($item in $Improved) { $changelogLines.Add("- $item") }
    $changelogLines.Add("")
}
$changelogLines.Add($Note)
$fullChangelog = ($changelogLines -join "`n").Trim()

$payload = [ordered]@{
    versionCode    = $VersionCode
    versionName    = $VersionName
    announcementId = "rezona-v$VersionName-$stamp"
    title          = $Title
    apkUrl         = "https://rezona-music-web.vercel.app"
    added          = $Add
    fixed          = $Fix
    improved       = $Improved
    changelog      = $fullChangelog
}

$jsonPath = Join-Path $PSScriptRoot "web\public\version.json"
$payload | ConvertTo-Json -Depth 4 | Set-Content -Path $jsonPath -Encoding UTF8

Write-Host "`n================ COMMUNITY POST (WA / DISCORD) ================" -ForegroundColor Cyan
Write-Host "# $Title"
Write-Host "1. Add :"
$Add | ForEach-Object { Write-Host "- $_" }
Write-Host "`n2. Fix :"
$Fix | ForEach-Object { Write-Host "- $_" }
Write-Host "`n3. Improved :"
$Improved | ForEach-Object { Write-Host "- $_" }
Write-Host "`n$Note"
Write-Host "==============================================================`n" -ForegroundColor Cyan

if ($Push) {
    git add "web/public/version.json"
    git commit -m "chore(release): publish in-app announcement $Title"
    git push origin main
}

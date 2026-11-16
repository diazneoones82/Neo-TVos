[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$Device,
    [string]$ApkPath = (Join-Path $PSScriptRoot 'build\Neo-TVLauncher.apk'),
    [string]$AdbPath,
    [string]$PairEndpoint,
    [switch]$ScanOnly,
    [switch]$SkipCleanup,
    [switch]$EnableHomeFix,
    [string]$IconPackPath,
    [switch]$DisableAutomaticSystemUpdates,
    [switch]$EnableAutomaticSystemUpdates,
    [string]$ReportDirectory = (Join-Path $PSScriptRoot 'device-reports')
)
$ErrorActionPreference = 'Stop'
if ($DisableAutomaticSystemUpdates -and $EnableAutomaticSystemUpdates) { throw 'Choose only one automatic-update scheduling option.' }
if (!$AdbPath) {
    $command = Get-Command adb -ErrorAction SilentlyContinue
    if ($command) { $AdbPath = $command.Source }
    elseif (Test-Path (Join-Path $PSScriptRoot 'platform-tools\adb.exe')) { $AdbPath = Join-Path $PSScriptRoot 'platform-tools\adb.exe' }
    else { throw 'Install Android Platform Tools and put adb on PATH, or pass -AdbPath.' }
}
if ($Device -notmatch '^[A-Za-z0-9_.:\[\]-]+$') { throw 'Invalid ADB device serial or endpoint.' }
if ($PairEndpoint) {
    if ($PairEndpoint -notmatch '^[A-Za-z0-9_.:\[\]-]+$') { throw 'Invalid pairing endpoint.' }
    & $AdbPath pair $PairEndpoint
    if ($LASTEXITCODE -ne 0) { throw 'Wireless pairing failed. Use the pairing address shown on the TV.' }
}
if ($Device -match '^\d{1,3}(\.\d{1,3}){3}$') { $Device += ':5555' }
if ($Device.Contains(':')) { & $AdbPath connect $Device | Out-Host }
function Invoke-TV {
    param([string[]]$Arguments, [switch]$AllowFailure)
    $output = @(& $AdbPath -s $Device @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0 -and !$AllowFailure) { throw "ADB failed: $($output -join ' ')" }
    return ($output -join "`n").Trim()
}
$state = Invoke-TV -Arguments @('get-state') -AllowFailure
if ($state -ne 'device') { throw "Target is not authorized/online: $Device. Accept Allow debugging on that TV and rerun. ADB says: $state" }
$currentUser = Invoke-TV -Arguments @('shell','am','get-current-user') -AllowFailure
if ($currentUser -match '^\d+$' -and $currentUser -ne '0') { throw 'Switch to the TV owner profile (user 0) before using this installer. No cleanup or installation performed.' }
$sdkText = Invoke-TV -Arguments @('shell','getprop','ro.build.version.sdk')
if ($sdkText -notmatch '^\d+$' -or [int]$sdkText -lt 24) { throw 'Neo requires Android 7.0 / API 24 or later. Tizen and webOS cannot run this APK.' }
$features = Invoke-TV -Arguments @('shell','pm','list','features')
$characteristics = Invoke-TV -Arguments @('shell','getprop','ro.build.characteristics')
if ($features -notmatch 'android.software.leanback|android.hardware.type.television' -and $characteristics -notmatch 'tv') {
    throw 'The selected device does not report Android TV capabilities. No changes made.'
}
$identity = [ordered]@{ device=$Device; sdk=[int]$sdkText }
foreach ($key in @('ro.product.brand','ro.product.manufacturer','ro.product.model','ro.build.version.release','ro.build.fingerprint')) {
    $identity[$key] = Invoke-TV -Arguments @('shell','getprop',$key)
}
Write-Host "`nTARGET: $($identity['ro.product.brand']) $($identity['ro.product.model']) at $Device"
$safeName = $Device -replace '[^A-Za-z0-9_.-]','_'
$runDirectory = Join-Path $ReportDirectory ($safeName + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
$identity | ConvertTo-Json | Set-Content (Join-Path $runDirectory 'device.json') -Encoding UTF8
$allText = Invoke-TV -Arguments @('shell','pm','list','packages','--user','0')
$thirdText = Invoke-TV -Arguments @('shell','pm','list','packages','-3','--user','0')
$systemText = Invoke-TV -Arguments @('shell','pm','list','packages','-s','--user','0')
$all = @($allText -split '\r?\n' | Where-Object { $_ -match '^package:' } | ForEach-Object { $_.Substring(8).Trim() })
$third = @($thirdText -split '\r?\n' | Where-Object { $_ -match '^package:' } | ForEach-Object { $_.Substring(8).Trim() })
$allText | Set-Content (Join-Path $runDirectory 'packages-before.txt') -Encoding UTF8
$systemText | Set-Content (Join-Path $runDirectory 'system-packages.txt') -Encoding UTF8
$thirdText | Set-Content (Join-Path $runDirectory 'third-party-packages.txt') -Encoding UTF8
$previousHome = Invoke-TV -Arguments @('shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME') -AllowFailure
$accessibility = Invoke-TV -Arguments @('shell','settings','get','secure','enabled_accessibility_services')
$previousHome | Set-Content (Join-Path $runDirectory 'previous-home.txt') -Encoding UTF8
$accessibility | Set-Content (Join-Path $runDirectory 'previous-accessibility.txt') -Encoding UTF8
$previousUpdateFlag = Invoke-TV -Arguments @('shell','settings','get','global','ota_disable_automatic_update') -AllowFailure
$previousUpdateFlag | Set-Content (Join-Path $runDirectory 'previous-update-scheduling.txt') -Encoding UTF8
$catalog = [ordered]@{
    'in.startv.hotstar' = 'Hotstar - subscription video and sports'
    'org.videolan.vlc' = 'VLC - USB, local and network media playback'
    'com.amazon.music.tv' = 'Amazon Music - music streaming'
    'org.jellyfin.androidtv' = 'Jellyfin - personal media-server client'
    'com.netflix.ninja' = 'Netflix - subscription video'
    'com.amazon.amazonvideo.livingroom' = 'Prime Video - subscription/rental video'
    'com.google.android.youtube.tv' = 'YouTube - online video'
    'com.google.android.youtube.tvmusic' = 'YouTube Music - music streaming'
    'com.google.android.videos' = 'Google TV / Play Movies - movies and purchased content'
    'com.google.android.play.games' = 'Google Play Games - gaming hub'
}
# Only these reviewed entertainment package IDs may ever enter this cleanup plan.
# Unknown user apps and OEM apps are inventoried but cannot be removed by this script.
$protectedPattern = '^(android$|com\.android\.|com\.realtek\.|com\.apps\.|com\.tvos\.neo($|\.)|com\.google\.android\.(gms|gsf|tvlauncher|apps\.tv\.launcherx|permissioncontroller|inputmethod|networkstack|providers|ext|modulemetadata|webview|packageinstaller|tv\.remote)(\.|$))'
$candidates = @()
foreach ($pkg in $catalog.Keys) {
    if ($all -notcontains $pkg -or $pkg -match $protectedPattern -or $previousHome -match ([regex]::Escape($pkg) + '/')) { continue }
    $details = Invoke-TV -Arguments @('shell','dumpsys','package',$pkg)
    $details | Set-Content (Join-Path $runDirectory ($pkg + '.txt')) -Encoding UTF8
    if ($details -match '(?m)^\s*(userId|sharedUserId)=1000\b|android.uid.system') { continue }
    $candidates += [pscustomobject]@{ Number=$candidates.Count+1; Package=$pkg; Purpose=$catalog[$pkg]; ThirdParty=($third -contains $pkg) }
}
$candidates | ConvertTo-Json -Depth 3 | Set-Content (Join-Path $runDirectory 'cleanup-candidates.json') -Encoding UTF8
Write-Host "Scanned $($all.Count) installed packages. Core Android, Settings, inputs, remotes, networking, updates, all launchers and unverified packages are excluded."
if ($candidates.Count) { $candidates | Format-Table Number,Package,Purpose -AutoSize | Out-Host }
else { Write-Host 'No apps from the reviewed optional-media catalog remain installed.' }
Write-Host "Inventory and recovery records: $runDirectory"
if ($ScanOnly) { Write-Host 'SCAN ONLY: no apps, launcher settings or accessibility services changed.'; return }
if (!(Test-Path -LiteralPath $ApkPath -PathType Leaf)) { throw "APK not found: $ApkPath. No cleanup performed." }

# Install and verify Neo before optional cleanup, so an install failure cannot strand cleanup work.
Write-Host "`nInstalling Neo..."
Invoke-TV -Arguments @('install','-r',(Resolve-Path -LiteralPath $ApkPath).Path) | Out-Host
if ($IconPackPath) {
    if (!(Test-Path -LiteralPath $IconPackPath -PathType Leaf)) { throw 'Optional icon-pack APK not found.' }
    Invoke-TV -Arguments @('install','-r',(Resolve-Path -LiteralPath $IconPackPath).Path) | Out-Host
}
$launch = Invoke-TV -Arguments @('shell','am','start','-W','-n','com.tvos.neo/.NeoHomeActivity')
$launch | Out-Host
if ($launch -notmatch 'Status: ok' -or $launch -match 'Error:|Exception') { throw 'Neo did not launch; cleanup was not started.' }
$selected = @()
if (!$SkipCleanup -and $candidates.Count) {
    $answer = Read-Host 'Enter OPTIONAL app numbers to remove, separated by commas (Enter skips cleanup)'
    if ($answer.Trim()) {
        $numbers = @($answer -split '[,\s]+' | Where-Object { $_ })
        foreach ($number in $numbers) {
            if ($number -notmatch '^\d+$' -or [int]$number -lt 1 -or [int]$number -gt $candidates.Count) { throw 'Invalid cleanup selection. No cleanup performed.' }
            $selected += $candidates[[int]$number-1]
        }
        $selected = @($selected | Sort-Object Package -Unique)
        $selected | Format-Table Package,Purpose -AutoSize | Out-Host
        Write-Host 'Removal is for Android user 0. APKs are copied first; app data/accounts are not backed up and will be removed.'
        $confirmed=$false
        while ($true) {
            $reply=Read-Host 'Remove only the displayed packages? [Y/N] (Enter = N)'
            if ([string]::IsNullOrWhiteSpace($reply) -or $reply -match '^(?i:n|no)$') { break }
            if ($reply -match '^(?i:y|yes)$') { $confirmed=$true; break }
            Write-Host 'Please enter Y or N.'
        }
        if (!$confirmed) { $selected=@(); Write-Host 'Cleanup skipped.' }
    }
}
$removalLog = @()
foreach ($app in $selected) {
    # Each item must still pass the catalog and protected-package gate immediately before mutation.
    if (!$catalog.Contains($app.Package) -or $app.Package -match $protectedPattern) { throw 'Protected or unreviewed package blocked.' }
    $backupDirectory = Join-Path (Join-Path $runDirectory 'apks') $app.Package
    New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
    $paths = Invoke-TV -Arguments @('shell','pm','path',$app.Package)
    $apkPaths = @($paths -split '\r?\n' | Where-Object { $_ -match '^package:/.+\.apk$' } | ForEach-Object { $_.Substring(8).Trim() })
    if (!$apkPaths.Count) { throw "No APK paths for $($app.Package). Removal blocked." }
    foreach ($path in $apkPaths) {
        Invoke-TV -Arguments @('pull',$path,(Join-Path $backupDirectory ([IO.Path]::GetFileName($path)))) | Out-Host
    }
    $result = Invoke-TV -Arguments @('shell','pm','uninstall','--user','0',$app.Package)
    $removalLog += [pscustomobject]@{ Package=$app.Package; Result=$result; ThirdParty=$app.ThirdParty; Backup=$backupDirectory }
    $removalLog | ConvertTo-Json -Depth 3 | Set-Content (Join-Path $runDirectory 'removals.json') -Encoding UTF8
    if ($result -notmatch '^Success') { throw "Removal failed for $($app.Package): $result" }
    Write-Host "Removed $($app.Package) for user 0"
}

# Preserve unrelated accessibility services; replace only the previous custom launcher service.
if ($DisableAutomaticSystemUpdates -or $EnableAutomaticSystemUpdates) {
    $updateValue = if ($DisableAutomaticSystemUpdates) { '1' } else { '0' }
    $writeResult = Invoke-TV -Arguments @('shell','settings','put','global','ota_disable_automatic_update',$updateValue) -AllowFailure
    $readback = Invoke-TV -Arguments @('shell','settings','get','global','ota_disable_automatic_update') -AllowFailure
    $updateReport = [ordered]@{ Previous=$previousUpdateFlag; Requested=$updateValue; Readback=$readback; FlagAccepted=($readback -eq $updateValue); ManufacturerUpdater='Not verified; vendor updaters may ignore the Android scheduling flag'; Scope='Android automatic system-update scheduling only; no updater packages disabled or removed' }
    $updateReport | ConvertTo-Json | Set-Content (Join-Path $runDirectory 'update-scheduling.json') -Encoding UTF8
    if ($readback -eq $updateValue) { Write-Host "Android automatic-update scheduling flag saved: $updateValue. Manufacturer updater enforcement is not verified." }
    else { Write-Warning "TV refused or does not expose the scheduling flag. No manufacturer updater was modified. $writeResult" }
}
Invoke-TV -Arguments @('shell','cmd','package','set-home-activity','com.tvos.neo/.NeoHomeActivity') -AllowFailure | Out-Host
if ($EnableHomeFix) {
    $existing = Invoke-TV -Arguments @('shell','settings','get','secure','enabled_accessibility_services')
    $services = @($existing -split ':' | Where-Object { $_ -and $_ -ne 'null' -and $_ -notmatch '^com\.tvos\.neo/' })
    $services += 'com.tvos.neo/com.tvos.neo.NeoHomeService'
    Invoke-TV -Arguments @('shell','settings','put','secure','enabled_accessibility_services',($services -join ':')) | Out-Null
    Invoke-TV -Arguments @('shell','settings','put','secure','accessibility_enabled','1') | Out-Null
    Write-Host 'Neo Home-button compatibility service enabled. Existing unrelated services preserved.'
} else { Write-Host 'If Home opens the old launcher: Neo Settings > Make Neo the Home launcher, then enable its accessibility service.' }
Invoke-TV -Arguments @('shell','am','start','-W','-n','com.tvos.neo/.NeoHomeActivity') | Out-Host
$after = Invoke-TV -Arguments @('shell','pm','list','packages','--user','0')
$after | Set-Content (Join-Path $runDirectory 'packages-after.txt') -Encoding UTF8
foreach ($app in $removalLog) { if (($after -split '\r?\n') -contains ('package:'+$app.Package)) { throw "Verification failed: $($app.Package) is still installed." } }
foreach ($pkg in @('android','com.android.systemui','com.android.tv.settings','com.android.bluetooth')) {
    if ($all -contains $pkg -and ($after -split '\r?\n') -notcontains ('package:'+$pkg)) { throw "Protected package verification failed: $pkg" }
}
@'
Recovery:
1. Disable Neo's Home-button service in Android Accessibility settings.
2. Choose the prior Home app (previous-home.txt); the installer never disables a stock launcher.
3. For preinstalled packages removed for user 0: adb -s DEVICE shell cmd package install-existing --user 0 PACKAGE
4. For third-party apps: reinstall from Play Store or use adb install-multiple with ALL APKs in apks/PACKAGE.
5. APK copies do not include former app data or accounts.
6. To remove Neo alone: adb -s DEVICE uninstall com.tvos.neo
Previous accessibility services are recorded in previous-accessibility.txt.
Automatic-update scheduling prior state is in previous-update-scheduling.txt. Restore an original
null with settings delete global ota_disable_automatic_update; otherwise restore its recorded
value with settings put global ota_disable_automatic_update VALUE. OEM update policies are separate.
'@ | Set-Content (Join-Path $runDirectory 'RECOVERY.txt') -Encoding UTF8
Write-Host "`nFinished. Test Home, Back, volume, TV inputs and Settings with your physical remote. Reboot once to verify your model's startup behavior."
Write-Host 'Neo supports sideloadable Android TV / Google TV API 24+, not Tizen/webOS. OEM Home policies and ADB access vary.'

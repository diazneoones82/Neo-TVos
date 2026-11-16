[CmdletBinding()]
param([string]$Device, [string]$AdbPath, [switch]$NoMenu)
$ErrorActionPreference = 'Stop'
$script:NeoDevice = $Device
$script:NeoIdentity = ''
$script:NeoAdb = $AdbPath
$script:NeoApk = Join-Path $PSScriptRoot 'build\TVOS-Neo.apk'
$script:NeoReports = Join-Path $PSScriptRoot 'device-reports'
$script:NeoExit = $false

function Confirm-NeoAction {
    param([string]$Title, [string]$Detail)
    Write-Host "`n$Title" -ForegroundColor Cyan
    if ($script:NeoDevice) { Write-Host "TV: $script:NeoDevice $script:NeoIdentity" }
    Write-Host $Detail
    while ($true) {
        $answer = Read-Host 'Proceed? [Y/N] (Enter = N)'
        if ([string]::IsNullOrWhiteSpace($answer) -or $answer -match '^(?i:n|no)$') { Write-Host 'Cancelled.'; return $false }
        if ($answer -match '^(?i:y|yes)$') { return $true }
        Write-Host 'Please enter Y or N.'
    }
}
function Resolve-NeoAdb {
    if ($script:NeoAdb) { return $script:NeoAdb }
    $command = Get-Command adb -ErrorAction SilentlyContinue
    if ($command) { $script:NeoAdb = $command.Source }
    elseif (Test-Path (Join-Path $PSScriptRoot 'platform-tools\adb.exe')) { $script:NeoAdb = Join-Path $PSScriptRoot 'platform-tools\adb.exe' }
    else { throw 'ADB not found. Extract Android Platform Tools beside this menu, or use option 17 to choose adb.' }
    return $script:NeoAdb
}
function Invoke-NeoAdb {
    param([string[]]$Arguments)
    $executable = Resolve-NeoAdb
    $result = @(& $executable @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    return ($result -join "`n").Trim()
}
function Require-NeoTarget {
    if (!$script:NeoDevice) { throw 'Choose option 1 or 2 to select the intended TV first.' }
    if ($script:NeoDevice -notmatch '^[A-Za-z0-9_.:\[\]-]+$') { throw 'Invalid TV endpoint or ADB serial.' }
}
function Assert-NeoTV {
    Require-NeoTarget
    $state = Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'get-state')
    if ($state -ne 'device') { throw 'TV is offline or unauthorized. Accept Allow debugging on that TV, then reconnect.' }
    $sdk = Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','getprop','ro.build.version.sdk')
    $features = Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','pm','list','features')
    $characteristics = Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','getprop','ro.build.characteristics')
    if ($sdk -notmatch '^\d+$' -or [int]$sdk -lt 24 -or ($features -notmatch 'android.software.leanback|android.hardware.type.television' -and $characteristics -notmatch 'tv')) { throw 'This action requires Android TV / Google TV API 24+. webOS and Tizen are unsupported.' }
}
function Read-NeoEndpoint {
    param([string]$Prompt)
    $value = (Read-Host $Prompt).Trim()
    if ($value -notmatch '^[A-Za-z0-9_.:\[\]-]+$') { throw 'Enter a TV IP:port or exact USB ADB serial.' }
    if ($value -match '^\d{1,3}(\.\d{1,3}){3}$') { $value += ':5555' }
    return $value
}
function Read-NeoFile {
    param([string]$Prompt,[string]$Default)
    $value = Read-Host "$Prompt$(if($Default){' [Enter: '+$Default+']'})"
    if ([string]::IsNullOrWhiteSpace($value)) { $value = $Default }
    if (!$value -or !(Test-Path -LiteralPath $value.Trim('"') -PathType Leaf)) { throw 'File not found.' }
    return (Resolve-Path -LiteralPath $value.Trim('"')).Path
}
function Read-NeoReport {
    $folder = (Read-Host "Report folder (under $script:NeoReports)").Trim('"')
    if (!(Test-Path -LiteralPath $folder -PathType Container)) { throw 'Report folder not found.' }
    $identityPath = Join-Path $folder 'device.json'
    if (!(Test-Path -LiteralPath $identityPath)) { throw 'This folder has no device.json identity record.' }
    $record = Get-Content -LiteralPath $identityPath -Raw | ConvertFrom-Json
    if ($record.device -ne $script:NeoDevice) { throw "Report belongs to $($record.device), not the selected TV. Select its exact endpoint first." }
    return (Resolve-Path -LiteralPath $folder).Path
}
function Invoke-NeoInstaller {
    param([switch]$Cleanup,[string]$IconPack)
    $arguments = @{ Device=$script:NeoDevice; AdbPath=(Resolve-NeoAdb); ApkPath=$script:NeoApk; EnableHomeFix=$true; ReportDirectory=$script:NeoReports }
    if (!$Cleanup) { $arguments.SkipCleanup=$true }
    if ($IconPack) { $arguments.IconPackPath=$IconPack }
    & (Join-Path $PSScriptRoot 'Setup-TVOS-Neo.ps1') @arguments
}
function Set-NeoUpdateFlag {
    param([ValidateSet('0','1','null')][string]$Value)
    Assert-NeoTV
    $previous = Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','settings','get','global','ota_disable_automatic_update')
    if ($previous -notin @('0','1','null')) { throw "TV did not expose a readable update setting: $previous" }
    $folder = Join-Path $script:NeoReports (($script:NeoDevice -replace '[^A-Za-z0-9_.-]','_')+'-updates-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    New-Item -ItemType Directory -Force -Path $folder | Out-Null
    @{device=$script:NeoDevice} | ConvertTo-Json | Set-Content (Join-Path $folder 'device.json') -Encoding UTF8
    $previous | Set-Content (Join-Path $folder 'previous-update-scheduling.txt') -Encoding UTF8
    if ($Value -eq 'null') { Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','settings','delete','global','ota_disable_automatic_update') | Out-Host }
    else { Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','settings','put','global','ota_disable_automatic_update',$Value) | Out-Host }
    $readback = Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','settings','get','global','ota_disable_automatic_update')
    @{Previous=$previous;Requested=$Value;Readback=$readback;FlagAccepted=($readback -eq $Value);ManufacturerUpdater='Enforcement unverified'} | ConvertTo-Json | Set-Content (Join-Path $folder 'update-scheduling.json') -Encoding UTF8
    Write-Host "Readback: $readback. Records: $folder"
    if ($readback -ne $Value) { throw 'TV did not retain the requested flag.' }
    Write-Host 'Android scheduling flag changed. Manufacturer firmware policies and Play Store updates are separate.'
}
function Remove-NeoHomeFix {
    $raw = Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','settings','get','secure','enabled_accessibility_services')
    $services = @($raw -split ':' | Where-Object { $_ -and $_ -ne 'null' -and $_ -notmatch '^com\.tvos\.neo/' })
    foreach($service in $services){if($service -notmatch '^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$'){throw 'Invalid accessibility component on TV; no configuration changed.'}}
    if ($services.Count) { Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','settings','put','secure','enabled_accessibility_services',("'"+($services -join ':')+"'")) | Out-Host }
    else { Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','settings','delete','secure','enabled_accessibility_services') | Out-Host }
    return ,$services
}
function Invoke-NeoChoice {
    param([string]$Choice)
    switch ($Choice) {
        '1' {
            $target = Read-NeoEndpoint 'TV IP[:port] or USB serial'
            if (!(Confirm-NeoAction 'Select / connect TV' "Connect only to $target, check authorization and show its manufacturer/model. No apps or settings change.")) { return }
            if ($target.Contains(':')) { Invoke-NeoAdb -Arguments @('connect',$target) | Out-Host }
            $oldTarget=$script:NeoDevice; $script:NeoDevice=$target
            try { Assert-NeoTV; $brand=Invoke-NeoAdb -Arguments @('-s',$target,'shell','getprop','ro.product.brand');$model=Invoke-NeoAdb -Arguments @('-s',$target,'shell','getprop','ro.product.model');$script:NeoIdentity="$brand $model";Write-Host "Selected: $script:NeoIdentity at $target" }
            catch { $script:NeoDevice=$oldTarget;throw }
        }
        '2' {
            $pair = Read-NeoEndpoint 'Wireless pairing endpoint shown on TV (IP:pairing-port)'
            $target = Read-NeoEndpoint 'Connection endpoint shown on TV (IP:connection-port)'
            if (!(Confirm-NeoAction 'Pair wireless debugging' "Pair with $pair, then connect to $target. ADB will ask for the code displayed on the TV.")) { return }
            Invoke-NeoAdb -Arguments @('pair',$pair) | Out-Host;Invoke-NeoAdb -Arguments @('connect',$target) | Out-Host
            $script:NeoDevice=$target;$script:NeoIdentity='';Assert-NeoTV
        }
        '3' { if (Confirm-NeoAction 'List connected devices' 'List ADB endpoints and authorization states. This may start the local ADB daemon; no TV settings change.') { Invoke-NeoAdb -Arguments @('devices','-l') | Out-Host } }
        '4' { Require-NeoTarget;if (Confirm-NeoAction 'Scan TV apps' 'Read device identity and installed packages; save inventory and cleanup candidates locally. No app removal or TV configuration.') { & (Join-Path $PSScriptRoot 'Setup-TVOS-Neo.ps1') -Device $script:NeoDevice -AdbPath (Resolve-NeoAdb) -ScanOnly -ReportDirectory $script:NeoReports } }
        '5' { Require-NeoTarget;if (Confirm-NeoAction 'Install / update Neo TVos' "Install $script:NeoApk, launch Neo, attempt default Home and enable its Home-button compatibility service. No cleanup removals.") { Invoke-NeoInstaller } }
        '6' { Require-NeoTarget;if (Confirm-NeoAction 'Install Neo and review cleanup' 'Install/launch Neo, then review optional media apps. You choose exact app numbers and confirm their removal with a second Y/N prompt. APKs are copied first; removed app data is not backed up. Core and unverified packages are excluded.') { Invoke-NeoInstaller -Cleanup } }
        '7' { Require-NeoTarget;$defaultPack=Join-Path $PSScriptRoot 'Neo-Outline-Icons.apk';if(!(Test-Path -LiteralPath $defaultPack)){$defaultPack=Join-Path $PSScriptRoot 'iconpack-example\build\Neo-Outline-Icons.apk'};$pack=Read-NeoFile 'Icon-pack APK' $defaultPack;if (Confirm-NeoAction 'Install Neo and icon pack' "Install/update Neo plus $pack and enable Home compatibility. No cleanup. Select the pack later in Neo Settings > Icon packs.") { Invoke-NeoInstaller -IconPack $pack } }
        '8' { Require-NeoTarget;if (Confirm-NeoAction 'Disable Android automatic-update scheduling' 'Save the prior value, then set ota_disable_automatic_update=1 and verify readback. No Neo reinstall. This does not prove manufacturer OTA updates are blocked; webOS/Tizen are unsupported.') { Set-NeoUpdateFlag '1' } }
        '9' { Require-NeoTarget;if (Confirm-NeoAction 'Enable Android automatic-update scheduling' 'Save the prior value, then set ota_disable_automatic_update=0 and verify readback. Manufacturer update policies remain separate. No Neo reinstall.') { Set-NeoUpdateFlag '0' } }
        '10' { Require-NeoTarget;$folder=Read-NeoReport;$value=(Get-Content -LiteralPath (Join-Path $folder 'previous-update-scheduling.txt') -Raw).Trim();if($value -notin @('0','1','null')){throw 'Invalid recorded scheduling value.'};if(Confirm-NeoAction 'Restore previous update scheduling' "Restore value '$value' from $folder. A recorded null removes the override. Save current state before changing it; verify readback."){Set-NeoUpdateFlag $value} }
        '11' {
            Require-NeoTarget;Write-Host '1 Aurora Glass   2 Violet Dusk   3 Midnight Contrast   4 Custom theme JSON'
            $pick=Read-Host 'Theme';$presets=@{'1'='aurora';'2'='violet';'3'='midnight'}
            if($presets.ContainsKey($pick)){$theme=Join-Path $PSScriptRoot ('assets\neo\themes\'+$presets[$pick]+'.json')}elseif($pick -eq '4'){$theme=Read-NeoFile 'Theme JSON' ''}else{throw 'Choose theme 1-4.'}
            $wallpaper=Read-Host 'Optional wallpaper image path (Enter keeps theme wallpaper)'
            if($wallpaper){$wallpaper=$wallpaper.Trim('"');if(!(Test-Path -LiteralPath $wallpaper -PathType Leaf)){throw 'Wallpaper file not found.'};$wallpaper=(Resolve-Path -LiteralPath $wallpaper).Path}
            if(Confirm-NeoAction 'Apply theme / wallpaper' "Send $theme$(if($wallpaper){' and '+$wallpaper}) to Neo on the selected TV. Neo validates it; invalid themes preserve the previous theme. App order is unchanged."){
                Assert-NeoTV;$arguments=@{ThemePath=$theme;Serial=$script:NeoDevice;Adb=(Resolve-NeoAdb)};if($wallpaper){$arguments.WallpaperPath=$wallpaper};& (Join-Path $PSScriptRoot 'Install-Theme.ps1') @arguments
            }
        }
        '12' { Require-NeoTarget;$package=(Read-Host 'Preinstalled package ID to restore').Trim();if($package -notmatch '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$'){throw 'Invalid package ID.'};if(Confirm-NeoAction 'Restore preinstalled app' "Restore $package for user 0 using its existing system APK. This cannot recover deleted account/app data or a third-party APK absent from firmware."){Assert-NeoTV;Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','cmd','package','install-existing','--user','0',$package)|Out-Host} }
        '13' { Require-NeoTarget;$folder=(Read-Host 'Folder containing ALL backed-up APK splits for one app').Trim('"');$files=@(Get-ChildItem -LiteralPath $folder -File -Filter '*.apk'|Sort-Object Name|Select-Object -ExpandProperty FullName);if(!$files.Count){throw 'No APKs found.'};if(Confirm-NeoAction 'Restore app from APK backup' ("Install these APKs together: `n"+($files -join "`n")+"`nFormer app data/accounts are not restored.")){Assert-NeoTV;$command=if($files.Count -eq 1){'install'}else{'install-multiple'};Invoke-NeoAdb -Arguments (@('-s',$script:NeoDevice,$command,'-r')+$files)|Out-Host} }
        '14' {
            Require-NeoTarget;$folder=Read-NeoReport;$raw=Get-Content -LiteralPath (Join-Path $folder 'previous-home.txt') -Raw
            $component=($raw -split '\r?\n'|ForEach-Object{$_.Trim()}|Where-Object{$_ -match '^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$'}|Select-Object -First 1)
            if(!$component -or $component -match '^com\.tvos\.neo/'){throw 'Choose an earlier report with a non-Neo Home activity.'}
            $recorded=(Get-Content -LiteralPath (Join-Path $folder 'previous-accessibility.txt') -Raw).Trim()
            $previous=@($recorded -split ':'|Where-Object{$_ -and $_ -ne 'null' -and $_ -notmatch '^com\.tvos\.neo/'})
            foreach($service in $previous){if($service -notmatch '^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$'){throw 'Invalid accessibility component in report.'}}
            if(Confirm-NeoAction 'Restore previous Home' "Disable Neo Home interception, restore $component, and merge recorded non-Neo accessibility services with current unrelated services. Neo remains installed."){
                Assert-NeoTV;$package=$component.Split('/')[0];$present=Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','pm','path',$package);if($present -notmatch '^package:'){throw 'Recorded Home app is no longer installed; no configuration changed.'};$current=Remove-NeoHomeFix;$services=@($current+$previous|Sort-Object -Unique)
                if($services.Count){Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','settings','put','secure','enabled_accessibility_services',("'"+($services -join ':')+"'"))|Out-Host;Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','settings','put','secure','accessibility_enabled','1')|Out-Host}
                Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','cmd','package','set-home-activity',("'"+$component+"'"))|Out-Host
                Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','am','start','-W','-n',("'"+$component+"'"))|Out-Host
            }
        }
        '15' {
            Require-NeoTarget;if(!(Confirm-NeoAction 'Prepare Neo removal' 'First inspect alternative Home apps. You will choose a fallback and receive a final Y/N confirmation before Neo and its local settings are removed.')){return}
            Assert-NeoTV;$raw=Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','cmd','package','query-activities','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
            $alternatives=@($raw -split '\r?\n'|ForEach-Object{$_.Trim()}|Where-Object{$_ -match '^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$' -and $_ -notmatch '^com\.tvos\.neo/'}|Sort-Object -Unique)
            if(!$alternatives.Count){throw 'No fallback Home found; removal blocked.'};for($i=0;$i -lt $alternatives.Count;$i++){Write-Host "$($i+1) $($alternatives[$i])"}
            $pick=Read-Host 'Fallback Home number (Enter cancels)';if(!$pick){return};if($pick -notmatch '^\d+$' -or [int]$pick -lt 1 -or [int]$pick -gt $alternatives.Count){throw 'Invalid fallback choice.'};$fallback=$alternatives[[int]$pick-1]
            if(Confirm-NeoAction 'Remove Neo TVos' "Disable Neo Home interception, choose $fallback as Home, and uninstall only com.tvos.neo. Neo categories/theme preferences are deleted. Other apps and update-scheduling state are unchanged."){
                Remove-NeoHomeFix|Out-Null;Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','cmd','package','set-home-activity',("'"+$fallback+"'"))|Out-Host;Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'uninstall','com.tvos.neo')|Out-Host
            }
        }
        '16' { if(Confirm-NeoAction 'Show reports and guide' "List local reports in $script:NeoReports and print the menu guide. No TV connection or changes."){if(Test-Path $script:NeoReports){Get-ChildItem -LiteralPath $script:NeoReports -Directory|Select-Object Name,FullName|Format-Table -AutoSize|Out-Host};Get-Content -LiteralPath (Join-Path $PSScriptRoot 'INSTALL.md')|Out-Host} }
        '17' { $defaultAdb='';try{$defaultAdb=Resolve-NeoAdb}catch{};$adb=Read-NeoFile 'ADB executable' $defaultAdb;$apk=Read-NeoFile 'Neo APK' $script:NeoApk;if(Confirm-NeoAction 'Choose local tool files' "Use ADB: $adb`nUse Neo APK: $apk`nSave for this menu session only. No executable is run now."){$script:NeoAdb=$adb;$script:NeoApk=$apk} }
        '18' {
            Require-NeoTarget;$minutes=Read-Host 'System idle delay in minutes (2,5,10,15,20,25,30)';if($minutes -notmatch '^(2|5|10|15|20|25|30)$'){throw 'Choose 2 or 5 to 30 minutes in steps of 5.'}
            if(Confirm-NeoAction 'Use Neo as system screensaver' "Back up the current screensaver settings, select NeoDreamService, enable idle screensavers and set the Android screen-off delay to $minutes minutes. OEM firmware may use a separate timer. Restore using option 19."){
                Assert-NeoTV
                $installed=Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','dumpsys','package','com.tvos.neo');if($installed -notmatch 'NeoDreamService'){throw 'Install Neo TVos 1.6 or newer first.'}
                $folder=Join-Path $script:NeoReports (($script:NeoDevice -replace '[^A-Za-z0-9_.-]','_')+'-screensaver-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'));New-Item -ItemType Directory -Force $folder|Out-Null
                @{device=$script:NeoDevice}|ConvertTo-Json|Set-Content (Join-Path $folder 'device.json')
                $values=@();foreach($key in @('screensaver_components','screensaver_enabled','screensaver_activate_on_sleep','screensaver_activate_on_dock')){$values+=@{scope='secure';key=$key;value=(Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','settings','get','secure',$key))}}
                $values+=@{scope='system';key='screen_off_timeout';value=(Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','settings','get','system','screen_off_timeout'))}
                ConvertTo-Json -InputObject $values|Set-Content (Join-Path $folder 'previous-screensaver.json')
                Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','settings','put','secure','screensaver_components','com.tvos.neo/.NeoDreamService')|Out-Host
                foreach($key in @('screensaver_enabled','screensaver_activate_on_sleep','screensaver_activate_on_dock')){Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','settings','put','secure',$key,'1')|Out-Host}
                Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','settings','put','system','screen_off_timeout',([string]([int]$minutes*60000)))|Out-Host
                $readback=Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','settings','get','secure','screensaver_components');if($readback -ne 'com.tvos.neo/.NeoDreamService'){throw "Selection not retained. Recovery report: $folder"};Write-Host "Neo selected. Recovery report: $folder. OEM trigger timing must be checked on the TV."
            }
        }
        '19' {
            Require-NeoTarget;$folder=Read-NeoReport;$values=@(Get-Content -LiteralPath (Join-Path $folder 'previous-screensaver.json') -Raw|ConvertFrom-Json)
            foreach($row in $values){if(($row.scope -eq 'secure' -and $row.key -in @('screensaver_components','screensaver_enabled','screensaver_activate_on_sleep','screensaver_activate_on_dock')) -or ($row.scope -eq 'system' -and $row.key -eq 'screen_off_timeout')){}else{throw 'Invalid screensaver report key'};if($row.value -notmatch '^[A-Za-z0-9_./:$,;-]+$'){throw 'Invalid report value'}}
            if(Confirm-NeoAction 'Restore TV screensaver' "Restore the saved screensaver selection, activation flags and screen-off delay from $folder. Neo stays installed."){Assert-NeoTV;foreach($row in $values){if($row.value -eq 'null'){Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','settings','delete',$row.scope,$row.key)|Out-Host}else{Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','settings','put',$row.scope,$row.key,("'"+$row.value+"'"))|Out-Host}}}
        }
        '20' {
            Require-NeoTarget;$file=Read-NeoFile 'Custom screensaver image (under 20 MB)' '';if((Get-Item -LiteralPath $file).Length -gt 20000000){throw 'Image must be under 20 MB.'}
            if(Confirm-NeoAction 'Upload custom screensaver image' "Upload $file to Neo's external files. In Neo choose Screensaver > Wallpaper > Custom > Use image uploaded by companion CLI; Neo validates and imports it."){Assert-NeoTV;Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'shell','mkdir','-p','/sdcard/Android/data/com.tvos.neo/files/screensaver')|Out-Host;Invoke-NeoAdb -Arguments @('-s',$script:NeoDevice,'push',$file,'/sdcard/Android/data/com.tvos.neo/files/screensaver/custom-image')|Out-Host}
        }
        '0' { if(Confirm-NeoAction 'Exit menu' 'Close this menu. No additional TV changes.'){$script:NeoExit=$true} }
        default { Write-Host 'Choose a listed number.' }
    }
}
function Show-NeoMenu {
    Write-Host "`n========== Neo TVos - TV manager ==========" -ForegroundColor Cyan
    Write-Host "Selected TV: $(if($script:NeoDevice){$script:NeoDevice+' '+$script:NeoIdentity}else{'none - choose option 1 or 2'})"
    Write-Host @'
 1  Select / connect TV             2  Pair wireless debugging
 3  List connected ADB devices      4  Scan apps (read only)
 5  Install / update Neo            6  Install + review app cleanup
 7  Install Neo + icon pack         8  Disable automatic update scheduling
 9  Enable update scheduling       10  Restore previous update setting
11  Apply theme / wallpaper        12  Restore preinstalled app
13  Restore backed-up APKs         14  Restore previous Home
15  Uninstall Neo                  16  Reports and guide
17  Choose ADB / APK files         18  Override system screensaver
19  Restore TV screensaver         20  Upload screensaver image
 0  Exit
'@
}
if (!$NoMenu) {
    while (!$script:NeoExit) {
        Show-NeoMenu
        try { Invoke-NeoChoice (Read-Host 'Option') } catch { Write-Host "Action stopped: $($_.Exception.Message)" -ForegroundColor Yellow }
        if (!$script:NeoExit) { [void](Read-Host 'Press Enter to return to the menu') }
    }
}


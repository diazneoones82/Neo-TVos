param(
    [Parameter(Mandatory=$true)][string]$ThemePath,
    [string]$WallpaperPath,
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb
)
$ErrorActionPreference = 'Stop'
if(!$Adb){$command=Get-Command adb -ErrorAction SilentlyContinue;if($command){$Adb=$command.Source}elseif(Test-Path (Join-Path $PSScriptRoot 'platform-tools\adb.exe')){$Adb=Join-Path $PSScriptRoot 'platform-tools\adb.exe'}else{throw 'Put adb on PATH or pass -Adb.'}}
if($Serial -notmatch '^[A-Za-z0-9_.:\[\]-]+$'){throw 'Invalid selected TV endpoint.'}
$raw = Get-Content -LiteralPath $ThemePath -Raw
$parsed = $raw | ConvertFrom-Json
if ($WallpaperPath) {
    $name = [IO.Path]::GetFileName($WallpaperPath)
    if ($name -notmatch '^[A-Za-z0-9_.-]+$' -or $name.Contains('..')) { throw 'Use a simple wallpaper filename' }
    & $Adb -s $Serial shell mkdir -p /sdcard/Android/data/com.tvos.neo/files/wallpapers
    & $Adb -s $Serial push $WallpaperPath "/sdcard/Android/data/com.tvos.neo/files/wallpapers/$name"
    if ($LASTEXITCODE -ne 0) { throw 'Wallpaper transfer failed' }
    $parsed.wallpaper = "file:$name"
    $raw = $parsed | ConvertTo-Json -Compress
}
$encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($raw))
& $Adb -s $Serial shell am start -W -n com.tvos.neo/.NeoHomeActivity --es neo_theme_base64 $encoded
if ($LASTEXITCODE -ne 0) { throw 'Theme delivery failed' }

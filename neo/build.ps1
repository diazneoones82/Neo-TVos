$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

. (Join-Path $PSScriptRoot '..\tools\Resolve-AndroidToolchain.ps1')
$toolchain=Resolve-NeoToolchain
$env:PATH="$($toolchain.JavaBin);$env:PATH"
$BT=$toolchain.BuildTools
$JAR=$toolchain.AndroidJar

if (Test-Path "build") {
    $buildPath = (Resolve-Path "build").Path
    if ($buildPath -ne (Join-Path $PSScriptRoot "build")) { throw "Unexpected build directory" }
    Get-ChildItem -LiteralPath $buildPath | Remove-Item -Recurse -Force
}
New-Item -ItemType Directory -Force -Path "build\gen", "build\classes", "build\dex" | Out-Null

& "$BT\aapt2.exe" compile --dir res -o build\res.zip
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

& "$BT\aapt2.exe" link -o build\unsigned.apk -I "$JAR" --manifest AndroidManifest.xml `
    --java build\gen --min-sdk-version 24 --target-sdk-version 34 `
    --version-code 202 --version-name "1" build\res.zip
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

$javaFiles = Get-ChildItem -Recurse -Path src, build\gen -Filter "*.java" | Select-Object -ExpandProperty FullName
$javaFiles | Out-File -Encoding ascii build\sources.txt
& javac -encoding UTF-8 --release 11 -classpath "$JAR" -d build\classes "@build\sources.txt"
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

& jar cf build\classes.jar -C build\classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }

& "$BT\d8.bat" --release --min-api 24 --lib "$JAR" --output build\dex build\classes.jar
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open((Resolve-Path "build\unsigned.apk").Path, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, (Resolve-Path "build\dex\classes.dex").Path, "classes.dex", [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null

    # Package assets with POSIX forward slashes (Windows aapt2 -A writes backslashes which Android AssetManager cannot open)
    $assetsRoot = (Resolve-Path "assets").Path
    $noCompressExts = @(".jpg", ".jpeg", ".png", ".webp", ".gif", ".mp3", ".wav", ".ogg", ".mp4", ".webm")
    Get-ChildItem -Recurse -File -Path $assetsRoot | ForEach-Object {
        $rel = $_.FullName.Substring($assetsRoot.Length + 1).Replace('\', '/')
        $entryName = "assets/$rel"
        if ($rel.StartsWith("neo/video/") -and $rel -ne "neo/video/imaginary-space-v1.mp4") { return }
        $existing = $archive.GetEntry($entryName)
        if ($existing) { $existing.Delete() }
        $ext = $_.Extension.ToLowerInvariant()
        $level = if ($noCompressExts -contains $ext) {
            [System.IO.Compression.CompressionLevel]::NoCompression
        } else {
            [System.IO.Compression.CompressionLevel]::Optimal
        }
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $_.FullName, $entryName, $level) | Out-Null
    }
} finally {
    $archive.Dispose()
}

& "$BT\zipalign.exe" -f 4 build\unsigned.apk build\aligned.apk
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

$KS = "debug.keystore"
if (-not (Test-Path $KS)) {
    & keytool -genkeypair -keystore $KS -storepass android -keypass android -alias debug `
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Neo TVLauncher development" | Out-Null
}

& "$BT\apksigner.bat" sign --ks $KS --ks-pass pass:android --key-pass pass:android `
    --out build\Neo-TVLauncher.apk build\aligned.apk
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

Write-Host "Built build\Neo-TVLauncher.apk"




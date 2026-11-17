param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb='adb',
    [string]$PairFile,
    [switch]$ProgressOnly,
    [string]$KeyStore=(Join-Path $PSScriptRoot '..\neo\debug.keystore')
)
$ErrorActionPreference='Stop'
if(!$ProgressOnly -and !$PairFile){throw 'Provide -PairFile for TLS/QR checks, or -ProgressOnly for action feedback checks'}
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Resolve-AndroidToolchain.ps1')
$toolchain=Resolve-NeoToolchain
$out=Join-Path $root 'companion\test-output\device'
New-Item -ItemType Directory -Force (Join-Path $out 'classes'),(Join-Path $out 'dex')|Out-Null
& "$($toolchain.BuildTools)\aapt2.exe" link -o "$out\base.apk" -I $toolchain.AndroidJar --manifest "$root\companion\tests\AndroidManifest.xml"
if($LASTEXITCODE){throw 'Test APK resources failed'}
& "$($toolchain.JavaBin)\javac.exe" -encoding UTF-8 --release 11 -cp "$($toolchain.AndroidJar);$root\companion\build\classes.jar" -d "$out\classes" "$root\companion\tests\PhoneRuntimeInstrumentation.java"
if($LASTEXITCODE){throw 'Test compilation failed'}
& "$($toolchain.JavaBin)\jar.exe" cf "$out\classes.jar" -C "$out\classes" .
& "$($toolchain.JavaBin)\java.exe" -cp "$($toolchain.BuildTools)\lib\d8.jar" com.android.tools.r8.D8 --release --min-api 26 --lib $toolchain.AndroidJar --classpath "$root\companion\build\classes.jar" --output "$out\dex" "$out\classes.jar"
if($LASTEXITCODE){throw 'Test dex failed'}
Copy-Item "$out\base.apk" "$out\unsigned.apk" -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::Open("$out\unsigned.apk",[IO.Compression.ZipArchiveMode]::Update)
try{[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,"$out\dex\classes.dex",'classes.dex')|Out-Null}finally{$zip.Dispose()}
& "$($toolchain.BuildTools)\zipalign.exe" -f 4 "$out\unsigned.apk" "$out\aligned.apk"
& "$($toolchain.JavaBin)\java.exe" -jar "$($toolchain.BuildTools)\lib\apksigner.jar" sign --ks $KeyStore --ks-pass pass:android --key-pass pass:android --out "$out\Neo-Runtime-Tests.apk" "$out\aligned.apk"
if($LASTEXITCODE){throw 'Test signing failed'}
& $Adb -s $Serial install --no-incremental -r "$out\Neo-Runtime-Tests.apk"
if($LASTEXITCODE){throw 'Test installation failed'}
try{
    if($ProgressOnly){$result=& $Adb -s $Serial shell am instrument -w com.neotvos.support.tests/com.neotvos.support.PhoneRuntimeInstrumentation}
    else{$pairingUri=(Get-Content -LiteralPath $PairFile -Raw).Trim();$result=& $Adb -s $Serial shell am instrument -w -e pairing $pairingUri com.neotvos.support.tests/com.neotvos.support.PhoneRuntimeInstrumentation}
    $result|Tee-Object -FilePath "$out\runtime-results.txt"|Out-Host
    $summary=$result -join "`n"
    if($summary -notmatch 'PASS:' -or $summary -match 'FAIL:'){throw 'Runtime assertions failed'}
}finally{& $Adb -s $Serial uninstall com.neotvos.support.tests|Out-Null}

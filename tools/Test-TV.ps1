param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb='adb',
    [string]$KeyStore=(Join-Path $PSScriptRoot '..\neo\debug.keystore')
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Resolve-AndroidToolchain.ps1')
$toolchain=Resolve-NeoToolchain
$out=Join-Path $root 'neo\test-output\device'
New-Item -ItemType Directory -Force (Join-Path $out 'classes'),(Join-Path $out 'dex')|Out-Null
& "$($toolchain.BuildTools)\aapt2.exe" link -o "$out\base.apk" -I $toolchain.AndroidJar --manifest "$root\neo\tests\AndroidManifest.xml"
if($LASTEXITCODE){throw 'Test APK resources failed'}
& "$($toolchain.JavaBin)\javac.exe" -encoding UTF-8 --release 11 -cp "$($toolchain.AndroidJar);$root\neo\build\classes.jar" -d "$out\classes" "$root\neo\tests\NeoRuntimeInstrumentation.java"
if($LASTEXITCODE){throw 'Test compilation failed'}
& "$($toolchain.JavaBin)\jar.exe" cf "$out\classes.jar" -C "$out\classes" .
& "$($toolchain.JavaBin)\java.exe" -cp "$($toolchain.BuildTools)\lib\d8.jar" com.android.tools.r8.D8 --release --min-api 24 --lib $toolchain.AndroidJar --classpath "$root\neo\build\classes.jar" --output "$out\dex" "$out\classes.jar"
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
    $result=& $Adb -s $Serial shell am instrument -w com.tvos.neo.tests/com.tvos.neo.NeoRuntimeInstrumentation
    $result|Tee-Object -FilePath "$out\runtime-results.txt"|Out-Host
    $summary=$result -join "`n"
    if($summary -notmatch 'PASS:' -or $summary -match 'FAIL:'){throw 'Runtime assertions failed'}
}finally{& $Adb -s $Serial uninstall com.tvos.neo.tests|Out-Null}

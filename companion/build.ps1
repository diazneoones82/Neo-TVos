$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
. (Join-Path $PSScriptRoot '..\tools\Resolve-AndroidToolchain.ps1')
$toolchain=Resolve-NeoToolchain
$java=$toolchain.JavaBin
$bt=$toolchain.BuildTools
$android=$toolchain.AndroidJar
$apk=Join-Path $PSScriptRoot '..\neo\build\Neo-TVLauncher.apk'
if(!(Test-Path -LiteralPath $apk)){throw 'Build Neo launcher first'}
New-Item -ItemType Directory -Force build\inventory-classes,build\inventory-dex|Out-Null
& "$java\javac.exe" -encoding UTF-8 --release 11 -classpath $android -d build\inventory-classes inventory-src\com\neotvos\support\InventoryMain.java src\com\neotvos\support\CleanupPolicy.java
if($LASTEXITCODE){throw 'Inventory helper compilation failed'}
& "$java\jar.exe" cf build\inventory-classes.jar -C build\inventory-classes .
& "$java\java.exe" -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --release --min-api 24 --lib $android --output build\inventory-dex build\inventory-classes.jar
if($LASTEXITCODE){throw 'Inventory helper DEX failed'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$helperFile=Join-Path $PSScriptRoot 'build\inventory.jar';if(Test-Path -LiteralPath $helperFile){Remove-Item -LiteralPath $helperFile -Force};$helper=[IO.Compression.ZipFile]::Open($helperFile,[IO.Compression.ZipArchiveMode]::Create)
try{[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($helper,(Join-Path $PSScriptRoot 'build\inventory-dex\classes.dex'),'classes.dex')|Out-Null}finally{$helper.Dispose()}
Copy-Item build\inventory.jar assets\inventory.jar -Force
Copy-Item -LiteralPath $apk -Destination assets\Neo-TVLauncher.apk -Force
(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()|Set-Content -LiteralPath assets\Neo-TVLauncher.sha256 -Encoding ascii
New-Item -ItemType Directory -Force build\gen,build\classes,build\dex|Out-Null
& "$bt\aapt2.exe" compile --dir res -o build\res.zip
if($LASTEXITCODE){throw 'Resource compile failed'}
& "$bt\aapt2.exe" link -o build\base.apk -I $android --manifest AndroidManifest.xml --java build\gen --version-code 152 --version-name '1' build\res.zip
if($LASTEXITCODE){throw 'Resource link failed'}
$files=@(Get-ChildItem src,build\gen,..\shared\src -Recurse -Filter *.java|ForEach-Object FullName)
& "$java\javac.exe" -encoding UTF-8 --release 11 -classpath $android -d build\classes @files
if($LASTEXITCODE){throw 'Java compile failed'}
& "$java\jar.exe" cf build\classes.jar -C build\classes .
& "$java\java.exe" -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --release --min-api 26 --lib $android --output build\dex build\classes.jar
if($LASTEXITCODE){throw 'DEX failed'}
Copy-Item build\base.apk build\unsigned.apk -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::Open((Join-Path $PSScriptRoot 'build\unsigned.apk'),[IO.Compression.ZipArchiveMode]::Update)
try{[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $PSScriptRoot 'build\dex\classes.dex'),'classes.dex')|Out-Null
$assetRoot=(Resolve-Path assets).Path
foreach($file in Get-ChildItem assets -Recurse -File){$entry='assets/'+$file.FullName.Substring($assetRoot.Length+1).Replace('\','/');$compression=if($file.Extension -eq '.apk'){[IO.Compression.CompressionLevel]::NoCompression}else{[IO.Compression.CompressionLevel]::Optimal};[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$file.FullName,$entry,$compression)|Out-Null}
}finally{$zip.Dispose()}
& "$bt\zipalign.exe" -f 4 build\unsigned.apk build\aligned.apk
if($LASTEXITCODE){throw 'Alignment failed'}
& "$java\java.exe" -jar "$bt\lib\apksigner.jar" sign --ks '..\neo\debug.keystore' --ks-pass pass:android --key-pass pass:android --out build\Neo-TVLauncher-Companion.apk build\aligned.apk
if($LASTEXITCODE){throw 'Signing failed'}
& "$java\java.exe" -jar "$bt\lib\apksigner.jar" verify build\Neo-TVLauncher-Companion.apk
if($LASTEXITCODE){throw 'Verification failed'}
Write-Output 'Built Neo-TVLauncher-Companion.apk (with bundled Neo launcher)'



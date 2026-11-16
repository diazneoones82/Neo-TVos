$ErrorActionPreference='Stop'
$tools=if($env:TVMAP_BUILD_TOOLS){$env:TVMAP_BUILD_TOOLS}else{Join-Path $env:ANDROID_HOME 'build-tools\34.0.0'}
$platform=if($env:TVMAP_ANDROID_JAR){$env:TVMAP_ANDROID_JAR}else{Join-Path $env:ANDROID_HOME 'platforms\android-34\android.jar'}
$output=Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Force $output | Out-Null
& "$tools\aapt2.exe" compile --dir (Join-Path $PSScriptRoot 'res') -o "$output\res.zip"
if($LASTEXITCODE){throw 'Icon pack resource compilation failed'}
& "$tools\aapt2.exe" link -o "$output\unsigned.apk" -I $platform --manifest (Join-Path $PSScriptRoot 'AndroidManifest.xml') --min-sdk-version 24 --target-sdk-version 34 --version-code 1 --version-name 1.0 "$output\res.zip"
if($LASTEXITCODE){throw 'Icon pack linking failed'}
& "$tools\zipalign.exe" -f 4 "$output\unsigned.apk" "$output\aligned.apk"
if($LASTEXITCODE){throw 'Icon pack alignment failed'}
& "$tools\apksigner.bat" sign --ks (Join-Path $PSScriptRoot '..\debug.keystore') --ks-pass pass:android --out "$output\Neo-Outline-Icons.apk" "$output\aligned.apk"
if($LASTEXITCODE){throw 'Icon pack signing failed'}
Write-Host "Built $output\Neo-Outline-Icons.apk"

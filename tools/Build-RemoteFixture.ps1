param([int]$VersionCode=1)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Resolve-AndroidToolchain.ps1')
$tc=Resolve-NeoToolchain
$out=Join-Path $root 'audit\remote-fixture'
$source=Join-Path $root 'tests\remote-fixture'
New-Item -ItemType Directory -Force "$out\classes","$out\dex","$out\assets"|Out-Null
Set-Content "$out\assets\split-proof.txt" 'This asset proves the phone split APK was transferred.'
& "$($tc.BuildTools)\aapt2.exe" link -o "$out\base-unsigned.apk" -I $tc.AndroidJar --manifest "$source\AndroidManifest.xml" --version-code $VersionCode --version-name $VersionCode
if($LASTEXITCODE){throw 'Fixture base resources failed'}
& "$($tc.JavaBin)\javac.exe" -encoding UTF-8 --release 11 -cp $tc.AndroidJar -d "$out\classes" "$source\FixtureActivity.java"
& "$($tc.JavaBin)\jar.exe" cf "$out\classes.jar" -C "$out\classes" .
& "$($tc.JavaBin)\java.exe" -cp "$($tc.BuildTools)\lib\d8.jar" com.android.tools.r8.D8 --release --min-api 24 --lib $tc.AndroidJar --output "$out\dex" "$out\classes.jar"
if($LASTEXITCODE){throw 'Fixture compilation failed'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::Open("$out\base-unsigned.apk",[IO.Compression.ZipArchiveMode]::Update)
try{[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,"$out\dex\classes.dex",'classes.dex')|Out-Null}finally{$zip.Dispose()}
& "$($tc.BuildTools)\aapt2.exe" link -o "$out\split-unsigned.apk" -I $tc.AndroidJar --manifest "$source\SplitManifest.xml" -A "$out\assets" --version-code $VersionCode --version-name $VersionCode
if($LASTEXITCODE){throw 'Fixture split resources failed'}
foreach($name in @('base','split')){
    & "$($tc.BuildTools)\zipalign.exe" -f 4 "$out\$name-unsigned.apk" "$out\$name-aligned.apk"
    & "$($tc.JavaBin)\java.exe" -jar "$($tc.BuildTools)\lib\apksigner.jar" sign --ks "$root\neo\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$out\$name.apk" "$out\$name-aligned.apk"
    if($LASTEXITCODE){throw 'Fixture signing failed'}
}
Write-Output "Built disposable base and split fixture in $out"

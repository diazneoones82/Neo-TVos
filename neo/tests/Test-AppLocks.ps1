param([string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
if(!$JavaHome){$javac=Get-Command javac.exe -ErrorAction Stop;$JavaHome=Split-Path (Split-Path $javac.Source -Parent) -Parent}
$root=Split-Path $PSScriptRoot -Parent
$out=Join-Path $root 'test-output\lock-host'
New-Item -ItemType Directory -Force $out|Out-Null
$files=@(Get-ChildItem (Join-Path $PSScriptRoot 'lock-host') -Recurse -Filter *.java|ForEach-Object FullName)
$files+=Join-Path $root 'src\com\techgenius\tvoslauncher\NeoAppLock.java'
& "$JavaHome\bin\javac.exe" -d $out @files
if($LASTEXITCODE){throw 'Compilation failed'}
& "$JavaHome\bin\java.exe" -cp $out com.tvos.neo.NeoAppLockTest
if($LASTEXITCODE){throw 'App lock tests failed'}

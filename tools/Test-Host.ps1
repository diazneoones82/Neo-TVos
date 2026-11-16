$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$javaHome=$env:JAVA_HOME
if(!$javaHome){$javaHome=Split-Path (Split-Path (Get-Command javac.exe -ErrorAction Stop).Source -Parent) -Parent}
$out=Join-Path $root 'neo\test-output\host'
New-Item -ItemType Directory -Force $out|Out-Null
& "$javaHome\bin\javac.exe" -encoding UTF-8 -d $out (Join-Path $root 'neo\src\com\techgenius\tvoslauncher\NeoGridNavigation.java') (Join-Path $root 'neo\tests\NeoGridNavigationTest.java') (Join-Path $root 'companion\src\com\neotvos\support\AdbClient.java') (Join-Path $root 'companion\src\com\neotvos\support\CleanupPolicy.java') (Join-Path $root 'companion\tests\CompanionTest.java')
if($LASTEXITCODE){throw 'Host compilation failed'}
foreach($class in @('com.tvos.neo.NeoGridNavigationTest','com.neotvos.support.CompanionTest')){& "$javaHome\bin\java.exe" -cp $out $class;if($LASTEXITCODE){throw "Failed $class"}}
& (Join-Path $root 'neo\tests\Test-AppLocks.ps1') -JavaHome $javaHome
& (Join-Path $root 'neo\tests\Test-NeoCLI.ps1')

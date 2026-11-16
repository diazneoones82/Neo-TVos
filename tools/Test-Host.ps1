$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$javaHome=$env:JAVA_HOME
if(!$javaHome){$javaHome=Split-Path (Split-Path (Get-Command javac.exe -ErrorAction Stop).Source -Parent) -Parent}
$out=Join-Path $root 'neo\test-output\host'
New-Item -ItemType Directory -Force $out|Out-Null
& "$javaHome\bin\javac.exe" -encoding UTF-8 -d $out (Join-Path $root 'neo\src\com\tvos\neo\NeoGridNavigation.java') (Join-Path $root 'neo\tests\NeoGridNavigationTest.java') (Join-Path $root 'companion\src\com\neotvos\support\AdbClient.java') (Join-Path $root 'companion\src\com\neotvos\support\CleanupPolicy.java') (Join-Path $root 'companion\tests\CompanionTest.java')
if($LASTEXITCODE){throw 'Host compilation failed'}
foreach($class in @('com.tvos.neo.NeoGridNavigationTest','com.neotvos.support.CompanionTest')){& "$javaHome\bin\java.exe" -cp $out $class;if($LASTEXITCODE){throw "Failed $class"}}
& "$javaHome\bin\javac.exe" -encoding UTF-8 -d $out (Join-Path $root 'neo\src\com\tvos\neo\NeoWakePolicy.java') (Join-Path $root 'neo\tests\NeoWakePolicyTest.java')
if($LASTEXITCODE){throw 'Wake policy compilation failed'}
& "$javaHome\bin\java.exe" -cp $out com.tvos.neo.NeoWakePolicyTest
if($LASTEXITCODE){throw 'Wake policy tests failed'}
& (Join-Path $root 'neo\tests\Test-AppLocks.ps1') -JavaHome $javaHome
& (Join-Path $root 'neo\tests\Test-NeoCLI.ps1')
$categoryOut=Join-Path $root 'neo\test-output\categories'
New-Item -ItemType Directory -Force $categoryOut|Out-Null
. (Join-Path $PSScriptRoot 'Resolve-AndroidToolchain.ps1')
$toolchain=Resolve-NeoToolchain
& "$javaHome\bin\javac.exe" -encoding UTF-8 -cp $toolchain.AndroidJar -d $categoryOut (Join-Path $root 'neo\src\com\tvos\neo\NeoCategories.java') (Join-Path $root 'neo\tests\NeoCategoriesTest.java')
if($LASTEXITCODE){throw 'Category test compilation failed'}
& "$javaHome\bin\java.exe" -cp "$categoryOut;$($toolchain.AndroidJar)" com.tvos.neo.NeoCategoriesTest
if($LASTEXITCODE){throw 'Category tests failed'}

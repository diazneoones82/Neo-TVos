function Resolve-NeoToolchain {
    $javaCommand=Get-Command javac.exe -ErrorAction SilentlyContinue
    if($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))){$javaBin=Join-Path $env:JAVA_HOME 'bin'}elseif($javaCommand){$javaBin=Split-Path $javaCommand.Source -Parent}else{throw 'Install JDK 17+ and set JAVA_HOME or add its bin folder to PATH.'}
    $sdk=if($env:ANDROID_HOME){$env:ANDROID_HOME}elseif($env:ANDROID_SDK_ROOT){$env:ANDROID_SDK_ROOT}else{Join-Path $env:LOCALAPPDATA 'Android\Sdk'}
    $bt=$env:TVMAP_BUILD_TOOLS
    if(!$bt){$bt=Get-ChildItem (Join-Path $sdk 'build-tools') -Directory -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Where-Object {Test-Path (Join-Path $_.FullName 'aapt2.exe')} | Select-Object -First 1 -ExpandProperty FullName}
    $jar=$env:TVMAP_ANDROID_JAR
    if(!$jar){$platform=Get-ChildItem (Join-Path $sdk 'platforms') -Directory -ErrorAction SilentlyContinue|Where-Object {$_.Name -match '^android-\d+$'}|Sort-Object {[int]($_.Name -replace '^android-','')} -Descending|Select-Object -First 1;if($platform){$jar=Join-Path $platform.FullName 'android.jar'}}
    if(!$bt -or !(Test-Path (Join-Path $bt 'aapt2.exe')) -or !$jar -or !(Test-Path $jar)){throw 'Install Android SDK Build-Tools and an SDK Platform (API 34+) using Android Studio. Set ANDROID_HOME, or TVMAP_BUILD_TOOLS and TVMAP_ANDROID_JAR.'}
    [pscustomobject]@{JavaBin=$javaBin;BuildTools=$bt;AndroidJar=$jar}
}

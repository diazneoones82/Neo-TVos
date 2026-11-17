$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$retired=@{}
Get-Content (Join-Path $PSScriptRoot 'retired-file-hashes.txt')|ForEach-Object {if($_){$retired[$_]=1}}
$findings=@()
$utf8=[Text.UTF8Encoding]::new($false,$true)
foreach($folder in @('neo\src','neo\res','neo\assets','companion\src','companion\res','companion\inventory-src','shared\src')){
    foreach($file in Get-ChildItem (Join-Path $root $folder) -Recurse -File){
        if($file.Extension -eq '.apk' -or $file.Name -eq 'inventory.jar'){continue}
        $bytes=[IO.File]::ReadAllBytes($file.FullName)
        # Match the checkpoint's canonical LF fingerprints for both text and binary files.
        try{$normalized=$utf8.GetString($bytes).Replace("`r`n","`n");$bytes=$utf8.GetBytes($normalized)}catch{}
        $algorithm=[Security.Cryptography.SHA256]::Create()
        try{$hash=([BitConverter]::ToString($algorithm.ComputeHash($bytes))).Replace('-','').ToLowerInvariant()}finally{$algorithm.Dispose()}
        if($retired.ContainsKey($hash)){$findings+=$file.FullName.Substring($root.Length+1)}
    }
}
if($findings.Count){throw ('Retired inherited files remain: '+($findings -join ', '))}
foreach($module in @(@('neo\src','com.tvos.neo'),@('companion\src','com.neotvos.support'),@('shared\src','com.neotv.protocol'))){
    foreach($source in Get-ChildItem (Join-Path $root $module[0]) -Recurse -Filter *.java){
        $declaration=Select-String -LiteralPath $source.FullName -Pattern '^package ([a-z0-9_.]+);'
        if(!$declaration -or $declaration.Matches[0].Groups[1].Value -ne $module[1]){throw "Unexpected runtime namespace: $($source.Name)"}
    }
}
$manifest=[xml](Get-Content (Join-Path $root 'neo\AndroidManifest.xml') -Raw)
$ns=[Xml.XmlNamespaceManager]::new($manifest.NameTable);$ns.AddNamespace('a','http://schemas.android.com/apk/res/android')
foreach($entry in @(@('.NeoCatalogService',':catalog'),@('.NeoWeatherService',':weather'),@('.NeoThemeService',':theme'),@('.NeoInstallService',':installer'))){
    $service=$manifest.SelectSingleNode("/manifest/application/service[@a:name='$($entry[0])']",$ns)
    if(!$service -or $service.GetAttribute('process',$ns.LookupNamespace('a')) -ne $entry[1]){throw "Missing worker boundary $($entry[0])"}
}
$theme=$manifest.SelectSingleNode("/manifest/application/service[@a:name='.NeoThemeService']",$ns)
if($theme.GetAttribute('isolatedProcess',$ns.LookupNamespace('a')) -ne 'true'){throw 'Theme worker must remain isolated'}
Write-Output "PASS: none of $($retired.Count) retired inherited fingerprints remain; runtime namespaces verified; four process boundaries and theme isolation retained."

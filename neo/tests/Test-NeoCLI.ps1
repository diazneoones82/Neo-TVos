$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '..\Neo-TVos-CLI.ps1') -NoMenu -Device 'test-tv:5555' -AdbPath 'fake-adb'
$fixtures=Join-Path $PSScriptRoot '..\..\audit\cli-tests'
New-Item -ItemType Directory -Force -Path $fixtures,(Join-Path $fixtures 'backup') | Out-Null
@{device='test-tv:5555'}|ConvertTo-Json|Set-Content (Join-Path $fixtures 'device.json')
'0'|Set-Content (Join-Path $fixtures 'previous-update-scheduling.txt')
'com.google.android.tvlauncher/.MainActivity'|Set-Content (Join-Path $fixtures 'previous-home.txt')
'null'|Set-Content (Join-Path $fixtures 'previous-accessibility.txt')
@{scope='secure';key='screensaver_enabled';value='1'}|ConvertTo-Json|Set-Content (Join-Path $fixtures 'previous-screensaver.json')
'fixture - never execute'|Set-Content (Join-Path $fixtures 'fake-adb.exe')
'fixture - never install'|Set-Content (Join-Path $fixtures 'backup\base.apk')
$script:Inputs=New-Object 'System.Collections.Generic.Queue[string]'
$script:Calls=New-Object 'System.Collections.Generic.List[string]'
function Read-Host {param([string]$Prompt) if(!$script:Inputs.Count){throw "Unexpected prompt: $Prompt"};return $script:Inputs.Dequeue()}
function Invoke-NeoAdb {param([string[]]$Arguments) $script:Calls.Add(($Arguments -join ' '));throw 'ADB must not run after N'}
function Invoke-NeoInstaller {param([switch]$Cleanup,[string]$IconPack) $script:Calls.Add('installer');throw 'Installer must not run after N'}
function Set-Answers {param([string[]]$Values) $script:Inputs.Clear();foreach($value in $Values){$script:Inputs.Enqueue($value)}}
function Check {param([bool]$Value,[string]$Message) if(!$Value){throw $Message}}
$cases=@(
    @{id='0';answers=@('N')},
    @{id='1';answers=@('192.0.2.10','N')},
    @{id='2';answers=@('192.0.2.10:37000','192.0.2.10:41000','N')},
    @{id='3';answers=@('N')},@{id='4';answers=@('N')},@{id='5';answers=@('N')},@{id='6';answers=@('N')},
    @{id='7';answers=@((Join-Path $fixtures 'backup\base.apk'),'N')},
    @{id='8';answers=@('N')},@{id='9';answers=@('N')},
    @{id='10';answers=@($fixtures,'N')},
    @{id='11';answers=@('1','','N')},
    @{id='12';answers=@('com.netflix.ninja','N')},
    @{id='13';answers=@((Join-Path $fixtures 'backup'),'N')},
    @{id='14';answers=@($fixtures,'N')},@{id='15';answers=@('N')},@{id='16';answers=@('N')},
    @{id='18';answers=@('5','N')},@{id='19';answers=@($fixtures,'N')},@{id='20';answers=@((Join-Path $fixtures 'backup\base.apk'),'N')},
    @{id='17';answers=@((Join-Path $fixtures 'fake-adb.exe'),(Join-Path $fixtures 'backup\base.apk'),'N')}
)
foreach($case in $cases){Set-Answers $case.answers;Invoke-NeoChoice $case.id;Check ($script:Inputs.Count -eq 0) "Unused answers for option $($case.id)";Check ($script:Calls.Count -eq 0) "Cancelled option $($case.id) ran a command"}
Check (!$script:NeoExit) 'N unexpectedly exited the menu'
Set-Answers @('');Check (!(Confirm-NeoAction 'Test' 'Blank must cancel')) 'Blank did not cancel'
Set-Answers @('invalid','yes');Check (Confirm-NeoAction 'Test' 'Invalid input must reprompt') 'Yes not accepted'
function Assert-NeoTV {}
$script:Flag='0';$script:Writes=0;$script:NeoReports=Join-Path $fixtures 'reports'
function Invoke-NeoAdb {
    param([string[]]$Arguments)
    $script:Calls.Add(($Arguments -join ' '))
    if($Arguments -contains 'put'){$script:Flag=$Arguments[-1];$script:Writes++;return ''}
    if($Arguments -contains 'get'){return $script:Flag}
    throw 'Unexpected mock command'
}
Set-Answers @('Y');Invoke-NeoChoice '8';Check ($script:Flag -eq '1' -and $script:Writes -eq 1) 'Y did not execute the approved mock action'
Set-Answers @('N');Invoke-NeoChoice '9';Check ($script:Flag -eq '1' -and $script:Writes -eq 1) 'N executed an update mutation'
Set-Answers @('Y');Invoke-NeoChoice '9';Check ($script:Flag -eq '0' -and $script:Writes -eq 2) 'Enable scheduling did not execute after Y'
Write-Output 'PASS: all 21 menu choices cancel without ADB/installer calls; blank/invalid answers and approved mock update actions passed.'

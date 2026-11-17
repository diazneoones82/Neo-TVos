# Remote installation testing

Use your own test phone and Android TV on the same private network. Build and install both APKs with the same local signing identity used by the device installations. Never publish a live pairing QR or signing key.

1. Open Neo Settings > Install apps remotely on the TV. Enable its APK-install permission if requested, then reopen pairing.
2. On the phone, open Install apps remotely > Scan TV QR. Aim at the complete square and confirm the displayed TV model. This optical step requires a real camera; a pasted URI does not prove camera scanning.
3. Build the disposable split fixture with `tools/Build-RemoteFixture.ps1 -VersionCode 1`. Install its base and split on the phone using `adb install-multiple`. Select Neo Remote Test in the phone chooser, send it, review on TV and confirm Android installation. Verify two installed APK paths and that the phone reports installed, rather than merely uploaded.
4. Build fixture version 2, update it on the phone, and repeat the remote transfer. Verify version 2 and its split on the TV. Remove only this test fixture from both devices afterwards.
5. Request an official Play listing from the phone. Approve on TV; verify the official Play Store handles the package. Complete any install in that store. Do not automate purchases or account acceptance.

For local diagnostics, save the current pairing URI in an ignored private file. While its TV screen remains open, run:

```powershell
.\tools\Test-Phone.ps1 -Serial '<phone-serial>' -Adb '<adb.exe>' -PairFile '<private-pair-file>'
python .\tools\Test-RemoteSecurity.py --pair-file '<private-pair-file>'
```

The phone test checks pinned TLS, rejection of a wrong certificate fingerprint and QR decoding on Android. The LAN suite checks bearer rejection, bounded manifests, invalid paths/hashes and cancellation without installing a package. It requires the locally built disposable fixture. Pairing expires after ten minutes; reopen it and replace the private file when needed. Close the TV pairing screen afterwards and verify the endpoint no longer accepts connections.

Host checks are `tools/Test-Host.ps1` and `tools/Verify-Runtime.ps1`. TV checks are `tools/Test-TV.ps1`; they include actual video surface recreation. These checks complement physical viewing of the space animation because some TV hardware video layers are absent from screenshots.

## Companion action feedback

`tools/Test-Phone.ps1 -Serial '<phone-serial>' -Adb '<adb.exe>' -ProgressOnly` exercises the actual phone UI and worker completion path without touching TV apps. It checks a real partial percentage, retained measurements while elapsed time updates, completion history/prompt, failure without success, and cancellation with stale-worker rejection. The same checks run before TLS/QR tests when PairFile is supplied.

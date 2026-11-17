# Validation and limitations

The launcher and phone companion were exercised on an Android 11 TV and Android phones. Tested flows include network ADB authorization, TV identification, actual app-name resolution, filtered general-app inventory, cancellation of cleanup, confirmed removal of disposable test packages, APK transfer/install, and OEM Home-button compatibility. Personal apps were not removed during those cleanup tests.

The normal Android Home command did not override the tested TV's OEM resolver. The explicitly confirmed Home-button compatibility path worked while retaining the original launcher and unrelated accessibility services.

Launcher checks include 36,900 bounded navigation cases, PIN hashing/retry/expiry/reboot behavior, category focus clipping, masked keypad layout, clock-hint focus visibility, automatic 2-minute idle activation, native DreamService startup, and cinematic playback through its loop boundary without black frames.

The space asset is authored at 1080p60. Hardware decoding was observed on the test TV; the TV's screen recorder itself has a lower frame rate, so its recording is not proof of 60-fps display on every device.

CLI checks cover cancellation of every menu choice without invoking ADB/install, Enter=N and invalid-answer handling, with mocked approved update actions. Protocol checks cover RSA authorization, repeated ADB channels, intact streamed APK bytes, malformed-packet/TLS rejection and cleanup protection.

USB discovery's no-device case was tested. Full phone-to-TV OTG transfer was not hardware-tested and requires compatible USB roles/ports. Pairing-code/TLS wireless debugging is not implemented by the phone companion. OEM settings, accessibility behavior and power/screen-saver timing may vary.

Only generic results are published. No device reports, serial numbers, LAN addresses, private screenshots or debugging keys are included.

## Runtime replacement validation

- The checkpoint retains the previous tree. All 451 identified inherited-file fingerprints are absent from the current runtime.
- Both portable source builds pass; final DEX inspection confirms the replacement input, symbol and forecast classes and absence of retired helpers/libraries/assets.
- Host suites pass: 36,900 bounded remote-navigation cases, categories, PIN/expiry/reboot, ADB authorization and streaming, cleanup protection, and 21 menu cancellation paths.
- Connected-TV instrumentation passes 59 runtime assertions, including all three forecast decoders, isolated theme UID, separate catalog/weather processes, catalog death/recovery, menus and unclipped focused cards.
- The companion embeds the exact new launcher APK with a matching SHA256. The phone was disconnected during this update, so a fresh companion hardware installation was not repeated.
- The new glass controls and lake background were visually checked on the connected TV. Older validation above describes earlier tests; it is not a claim that every old test was repeated for this version.

The final device run also verified restoration of the previously enabled Home accessibility service after instrumentation. Three consecutive Android Settings → remote Home cycles returned to Neo. The TV firmware still resolves its OEM launcher as the default, so Neo uses its explicitly enabled Home compatibility service; no system launcher was disabled or removed. No Neo crash was found in the crash buffer during this check.

## Neo TVLauncher Version 1

The rebranded launcher (Android code 200, version name 1) was upgraded in place on the connected TV. Both APK builds, host suites, the 451-fingerprint exclusion check and all 59 TV runtime assertions passed again. Home returned to the launcher from Android Settings; the accessibility binding was restored and had no crashed service. The companion (code 140, version name 1) embeds the exact new launcher and matching SHA256; its label was verified in the built APK. The phone was not connected for an additional installation.

## Original space scene update

The new scene uses generated black-hole/galaxy PNGs and a custom periodic orbital-light renderer. The prior external visualization and its render pipeline are absent from the current source/assets. Space video validation checks the real encoded frame rate, duration, dark center, visible disk motion, boundary-frame continuity and retained spacecraft/debris byte identity. The optional breathing-blue engines are a separate, playback-clock-synchronized layer with an Off/On setting; tests cover disabled/enabled layers, banking coordinates, periodic breathing and actual video playback after seeking. Historical checkpoint assets remain historical only.

The final encoded scene passed 1080p60/32-second checks, a 40.28 dB loop-boundary PSNR check (mean pixel difference 1.40/255), disk-motion and dark-center checks, and unchanged spacecraft/debris hashes. Both APKs built; the connected TV passed 72 runtime assertions including live looping playback, seeking and optional exhaust. Only `imaginary-space-v1.mp4` is packaged as video, and the companion embeds the exact new APK.

## Standby recovery update (Version 1, internal codes 202 / 142)

Host wake-policy tests cover Home-origin sleep, dream overlays, keyguard, unrelated-app preservation, pending-request cancellation and state restored on rebind. The connected TV passed 74 runtime assertions, including a real MediaPlayer recovery after temporary surface destruction/recreation. Two actual Android system-dream entries and remote-key exits returned to Neo Home. Finally, the TV was put into standby while Neo's system dream was active and awakened with the physical remote Power button; the user confirmed Home returned without wall-power cycling. Device inspection then showed Awake/display ON, NeoHomeActivity resumed, no active dream and no crashed Neo accessibility service. This verifies the tested cycle; it is not a guarantee for every TV firmware or intermittent future failure.

## QR remote installation and full-ring motion (internal codes 210 / 150)

Host tests pass for navigation, PIN/timed access, cleanup protection, CLI cancellation, independently implemented QR/RS decoding, bounded split manifests and persistent TLS identity. The retired-file check finds none of the 451 inherited unchanged fingerprints. The TV passes 77 runtime assertions including installer process isolation and actual SurfaceView destruction/recreation. A software JCA TLS identity is used after the Acer's hardware-keystore signing proved incompatible with its TLS provider. The pinned phone client rejects a deliberately wrong certificate fingerprint. LAN integration checks reject absent/incorrect bearers, invalid hashes/paths, oversized bodies and malformed store packages, refuse competing uploads and verify cancellation without installation.

The phone camera physically scanned the displayed TV QR and showed the TV-model confirmation. An own-code disposable base/split fixture was transferred from phone to TV, approved in the Neo review and Android installer, then launched. Its package recorded Neo as installer and both APK paths; the phone reported authoritative installation success. The official-store test opened the requested package in the actual TV Play Store; it did not install an unwanted previously removed app.

The final generated movie is 1920×1080 at 60fps for 32 seconds. Upper and lower arcs both carry measurable spatial motion, alongside the disk. Its lossless boundary motion is 1.003 times normal adjacent-frame motion; encoded boundary mean error is 1.795/255 and PSNR is 35.759 dB. Absolute PSNR is diagnostic for this stronger moving scene; the motion-relative source seam and decoded mean error are checked separately. The spacecraft/debris PNGs remain byte-identical. Exhaust uses small cached sprites anchored at the rear tip behind the ring, with full hull-alpha masking. Physical visibility remains a separate user check because the TV hardware video plane is omitted from screenshots.

The final phone-to-TV fixture update transferred both base and required split, installed version 2 through TV review and Android confirmation, and produced authoritative success on both screens. The receiver stopped accepting connections after Close pairing. Only the owned test fixture was removed from both devices afterwards.

After installing the final rear-tip exhaust build, all 77 TV runtime assertions passed again. The user physically confirmed that the blue jet placement and upper/lower swirl look good. The temporary keep-awake setting was restored to its original value, and the previously enabled Neo Home accessibility binding was restored without replacing unrelated services.

The official-store path was also verified through completion. From the companion, a package request for the free Send files to TV app was approved on the TV, opened in Google Play, and installed using the store's Install control. Android recorded `com.android.vending` as installer and the store changed to Open/Uninstall. The package was absent before this test and was removed afterwards; no previously installed app was removed. Pairing was closed and Home restored.

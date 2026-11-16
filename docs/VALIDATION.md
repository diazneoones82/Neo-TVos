# Validation and limitations

The launcher and phone companion were exercised on an Android 11 TV and Android phones. Tested flows include network ADB authorization, TV identification, actual app-name resolution, filtered general-app inventory, cancellation of cleanup, confirmed removal of disposable test packages, APK transfer/install, and OEM Home-button compatibility. Personal apps were not removed during those cleanup tests.

The normal Android Home command did not override the tested TV's OEM resolver. The explicitly confirmed Home-button compatibility path worked while retaining the original launcher and unrelated accessibility services.

Launcher checks include 36,900 bounded navigation cases, PIN hashing/retry/expiry/reboot behavior, category focus clipping, masked keypad layout, clock-hint focus visibility, automatic 2-minute idle activation, native DreamService startup, and cinematic playback through its loop boundary without black frames.

The space asset is authored at 1080p60. Hardware decoding was observed on the test TV; the TV's screen recorder itself has a lower frame rate, so its recording is not proof of 60-fps display on every device.

CLI checks cover cancellation of every menu choice without invoking ADB/install, Enter=N and invalid-answer handling, with mocked approved update actions. Protocol checks cover RSA authorization, repeated ADB channels, intact streamed APK bytes, malformed-packet/TLS rejection and cleanup protection.

USB discovery's no-device case was tested. Full phone-to-TV OTG transfer was not hardware-tested and requires compatible USB roles/ports. Pairing-code/TLS wireless debugging is not implemented by the phone companion. OEM settings, accessibility behavior and power/screen-saver timing may vary.

Only generic results are published. No device reports, serial numbers, LAN addresses, private screenshots or debugging keys are included.

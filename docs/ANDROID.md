# Neo TVLauncher Companion — phone installation guide

Install **Neo-TVLauncher-Companion.apk on your Android phone**, not on the TV. It contains the Neo TVLauncher launcher APK, so the install step works offline after the phone and TV are connected. Android 8 or newer is required on the phone; Android TV / Google TV 7 or newer is required on the TV.

## First prepare the TV

1. Open TV Settings > System / Device preferences > About.
2. Select Build seven times. Look for “You are now a developer”. Names vary by brand.
3. Return to Settings > Developer options. Enable USB debugging.
4. For network use, also enable Network / ADB debugging if the TV offers it. Find the TV's IP address in its Network settings. An Ethernet connection alone does not enable ADB.

## Connect using Wi-Fi or Ethernet

1. Connect the phone and TV to the same local network. The TV can use Ethernet while the phone uses Wi-Fi. Avoid guest networks or router client isolation.
2. In the phone app, select **Connect using Wi-Fi / Ethernet**.
3. Enter the TV's current IP address and its classic ADB port, commonly 5555. Or scan the local network and enter a candidate IP. A scan result is not automatically trusted or selected.
4. Select **Connect to this TV**.
5. On the TV, approve the debugging prompt for this phone. It may say “Allow USB debugging?” even for a network connection. Approve only a phone you recognize.
6. Check the detected brand, model, serial, Android version and target address before continuing.

This version supports classic authorized Network ADB. Pairing-code/TLS-only wireless-debugging ports are not implemented. If your TV only supports that path, first enable classic network ADB from a computer after pairing, or use a supported USB connection. The app detects a TLS port and explains the limitation rather than treating it as a successful connection.

## Connect using USB

1. Use an Android phone with USB host / OTG support and a data cable/OTG adapter.
2. Connect it to a TV or TV-box port documented to support USB device debugging. Many TV USB-A ports are host-only and cannot be used for this; do not assume a USB-A-to-A cable makes them work.
3. In the phone app, select **Scan USB debugging devices**.
4. Select the detected ADB device and grant the phone's USB permission.
5. Approve debugging on the TV and check the displayed identity.

If no ADB interface is found, check the cable, OTG support and TV port, or use the network route. A working USB ADB connection can be switched to classic network port 5555 using the separately confirmed option in the TV-details page. Find the TV IP, then reconnect over Wi-Fi/Ethernet. Normal use does not require USB debugging on the phone; it is only needed if you separately debug the phone itself.

## Review apps and cleanup

1. Select **Review general apps**.
2. Only eligible launchable general apps are shown, with the actual app name and package name. Required Android components, Settings, Home providers and input services are hidden. Reviewed optional preinstalled media apps can still appear.
3. Tap one app card and read the confirmation.
4. Choose **Yes, proceed** only for that app, or Cancel to leave it installed. Repeat separately for other apps.

Core/system apps, Settings, input/keyboard services, Neo itself and all detected Home providers are protected. Unknown system packages are not eligible. Optional reviewed media packages and third-party apps can be selected. This app makes no automatic app backup; removing an app can delete accounts/settings/data. Preinstalled packages may be restorable from firmware; downloaded apps require reinstalling.

## Install Neo and set up Home

1. Choose **Install Neo TVLauncher**, check the selected TV, then confirm. Keep the phone connected until the success message.
2. The app verifies its bundled APK's SHA256 before streaming it directly to Android's package installer. Other TV apps are not removed during installation.
3. Choose **Set Neo as Home > Set Android default Home to Neo**.
4. If the TV keeps its OEM launcher, choose **Enable Home-button compatibility**, read the accessibility-service explanation, and confirm.
5. Press Home on the TV remote. Neo should open. The original launcher remains installed.
6. **Undo companion-enabled Home compatibility** removes only the Neo accessibility component added by this phone, preserving other current accessibility services. If Neo was already enabled before, it stays enabled.

Neo's Home service also supports app-lock expiry by observing foreground package names; it does not inspect screen contents. Android Settings remains available. OEM firmware behavior can vary.

## Appearance and finishing

The phone app uses Neo's Aurora Glass palette, lake background and rounded glass cards/dialogs. **Theme Studio** also provides Violet Dusk and Midnight Contrast. It respects the phone's font-size accessibility settings.

Use the top Disconnect link to close or cancel the connection. If you no longer need debugging, disable it in the TV's Developer options. The desktop CLI remains available for recovery and advanced settings.

## Tested and limited

The phone-to-TV network workflow, identification, general-app inventory, cancellation, disposable-app removal, APK installation and Home-button compatibility were tested. Full USB/OTG installation has not been hardware-tested. Pairing-code/TLS wireless debugging is not supported by this phone release; use the Windows CLI for pairing.

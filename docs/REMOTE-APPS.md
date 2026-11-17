# Install apps remotely from an Android phone

Install Neo TVLauncher on the TV first using the existing Android support setup or Windows CLI. After that, the QR remote-app feature works over your local Wi-Fi/Ethernet network without USB/network debugging.

## Pair the phone with the TV

1. On the TV, open **Neo Settings → Install apps remotely**.
2. If requested, choose **Allow APK installs**, select **Neo TVLauncher**, and enable it. Some TV settings return to Home afterwards: reopen **Install apps remotely**. This setup happens before the pairing QR is shown.
3. Keep the TV pairing screen open. Connect the phone and TV to the same local network. Ethernet on the TV and Wi-Fi on the phone work together.
4. On the phone, open **Neo TVLauncher Companion → Install apps remotely / Scan TV QR → Scan TV QR**.
5. Allow Camera permission and point at the entire white QR square. The camera decoder runs on the phone. A compatible external QR reader can also open the `neotv://pair/` link in the companion; Paste pairing link is available.
6. Confirm the displayed TV model and address. The QR lasts ten minutes; close and reopen the TV screen to create a fresh one if it expires.

## Send an app already installed on the phone

1. Choose **Choose an app installed on this phone**.
2. Search its name or package. The list contains user-installed launchable apps, with names and package identifiers.
3. Select the app and review the destination, size and APK count, then Continue.
4. Keep the phone and TV awake during transfer. The companion includes the base APK and installed split APKs together.
5. On the TV, review the app name and package, then choose **Approve request**.
6. Confirm **Install** in Android's own installer. The phone reports success only after Android confirms installation.

App data, sign-ins, extra game downloads and store licences are not copied. A phone-only UI may need touch input. The companion checks Android minimum version and native CPU-library compatibility; Android makes the final package/signature/compatibility decision. Google Play on the TV is preferable when its TV-specific build differs from the phone build.

Transfers support up to 32 APK files and 1 GB per app. Core, system and launcher replacements are blocked through this route. Use the official TV store for preinstalled app updates and the companion's existing Neo setup/update route for Neo itself.

## Get an app from Google Play

1. In the paired phone page choose **Install from Google Play on TV**.
2. Enter the app's package name or paste its `https://play.google.com/store/apps/details?id=...` link.
3. Confirm the intended TV and send the request.
4. On the TV choose **Approve request**. Neo opens the official TV Play Store listing.
5. Choose **Install** in Google Play. Account, availability, purchase and TV-compatibility requirements are handled by Google Play.

The phone's “store opened” result means the listing opened; finish and check installation in Google Play on the TV. The companion does not download/extract APKs from Google's store. Google Play's own **Install on more devices** option can also be used when offered for the signed-in account and TV. On boxes without a Play Store, send a compatible app already installed on the phone instead.

## Finish or cancel

Cancel a pending transfer/request from the phone or TV. If Android's installer is already showing its confirmation, choose Cancel there as well. Closing the TV pairing screen stops the receiver. If a connection drops during installation, check the TV for the final result before retrying.

The receiver uses TLS pinned to the scanned certificate, a random short-lived secret, size/path/hash validation, and the normal Android installer. QR credentials and app files are not uploaded to a cloud service. The receiver runs in a separate `:installer` process and is closed by default.

## Visible progress

The companion shows file-check and transfer percentages, elapsed time, completed-stage checkmarks and completion prompts. During TV review or Android approval it shows an ongoing waiting bar; approve on the TV to advance. Installation completed appears only after the TV reports success. Errors and cancelled requests stop without a success indicator. A Play Store handoff confirms only that the store opened.

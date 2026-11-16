# Windows CLI guide

## Quick start

1. Download the Windows CLI ZIP from the project's GitHub Releases.
2. Extract the whole ZIP to a writable folder.
3. Get Android Platform Tools from https://developer.android.com/tools/releases/platform-tools . Put its `platform-tools` folder beside `Start-Neo-TVos.cmd`, add ADB to PATH, or select `adb.exe` with option 17.
4. Enable the TV's Developer options and USB/network debugging. Keep the TV on.
5. Double-click `Start-Neo-TVos.cmd` and choose option 1. Enter the TV IP:port or exact USB serial. Accept the debugging prompt on that TV.
6. Check its identity and choose option 4 to scan before changing anything.
7. Choose 5 to install/update without cleanup, or 6 to review optional media-app cleanup during installation.

Every option shows a brief explanation and asks Y/N before running. Enter or N cancels. Cleanup displays the exact chosen packages and asks a separate Y/N before removal. Installation is verified before cleanup is offered. Keep the original launcher available.

For pairing-code wireless debugging, use option 2. The pairing endpoint/code and the connection endpoint are different values displayed by the TV. The CLI never silently chooses the first connected device.

## Menu

| Option | Action |
| --- | --- |
| 1 / 2 / 3 | Select/connect, pair, or list devices |
| 4 | Read-only app scan and local report |
| 5 | Install/update Neo without cleanup |
| 6 | Install Neo and review optional cleanup |
| 7 | Install Neo with an icon pack |
| 8 / 9 / 10 | Update scheduling flag: disable, enable, or restore saved state |
| 11 | Apply a theme and optional wallpaper |
| 12 / 13 | Restore a preinstalled package or backed-up APK set |
| 14 / 15 | Restore previous Home, or prepare Neo removal with fallback Home |
| 16 / 17 | Reports/guide, or choose local ADB/APK paths |
| 18 / 19 | Select Neo as native screensaver, or restore saved screensaver settings |
| 20 | Upload a custom screensaver image |
| 0 | Exit |

Reports and backups are written locally under `device-reports`. Keep them private and retain them for recovery. Restore actions require the selected TV to match the report. Backup APK files do not restore deleted application data or accounts.

System update flags and native screensaver timing are manufacturer-dependent; this is not a universal firmware-update blocker. The tool does not support webOS or Tizen.

If the Home button still opens the OEM launcher, use Neo's Home-button compatibility/accessibility setting. The installer preserves unrelated accessibility services. Do not disable or remove the original launcher simply to force a replacement.

## Source checkout

The scripts live under `neo/` in the source repository. Build the launcher first or use option 17 to select a downloaded release APK. The downloadable Windows bundle already includes the APK under `build/TVOS-Neo.apk`.

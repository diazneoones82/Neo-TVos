# Neo runtime

The Version 1 runtime keeps the existing Neo behavior while retiring the unused inherited launcher implementation. The checkpoint before replacement is `checkpoint/pre-independent-runtime-20261116`; a local Git bundle also preserves it. Checkpoints and older releases remain historical records with their original notices.

## Modules and process boundaries

| Module | Responsibility | Boundary |
| --- | --- | --- |
| NeoHomeActivity | Glass home, categories, controls, dialogs and remote focus | Main application process |
| NeoCatalogService | Installed-app and icon-pack discovery | `:catalog` Binder worker |
| NeoWeatherService + NeoForecast | City lookup, location, provider adapters and cache | `:weather` Binder worker |
| NeoThemeService | Validate imported theme tokens | `:theme`, isolated UID, no network/files |
| NeoInstallService | Expiring QR-paired TLS receiver and reviewed package sessions | `:installer` Binder worker |
| NeoInputs | Read the Android TV hardware-input registry | Small platform adapter |
| NeoSymbols | Draw Neo control marks | Local Canvas rendering |
| NeoHomeService | Optional Home interception and access-window expiry | Accessibility service |
| NeoDreamService | System screensaver integration | Android DreamService |

This is an Android service architecture inspired by microkernel boundaries, not an operating-system microkernel. Catalog, weather and the installer have separate processes but share the application UID; theme validation additionally uses an isolated UID. UI requests are asynchronous with bounded timeouts. Worker death does not terminate Home; requests can reconnect.

## Replacement scope

The baseline audit identified **451 unchanged inherited files**. Those files are absent from the current runtime tree. Unused features, artwork, fonts and bundled libraries were removed rather than recreated as dead code. Every helper that the Neo home actually called was replaced: forecast/location/cache handling, hardware-input discovery, control symbols and typography. Typography now uses Android system fonts. Existing Neo-authored categories, PIN controls, clocks, screensavers, focus motion and companion transports remain.

New forecast adapters consume documented Open-Meteo, MET Norway and wttr.in responses; location uses ipwho.is. Provider fallback and cached forecasts remain. Existing preference keys and the forecast cache's displayed fields remain readable. The home displays current conditions and daily forecasts; no hidden hourly-weather UI is claimed. The historical `inter` theme token maps to a system medium-weight font so saved themes continue to work without shipping font files.

The code was written after observing the prior implementation and behavior; this is **not a clean-room provenance certification**. File-fingerprint checks prove that the identified unchanged files are gone, not that independently written programs cannot share common API calls or standard patterns.

## Verification

```powershell
.\tools\Verify-Runtime.ps1
.\tools\Test-Host.ps1
.\neo\build.ps1
.\companion\build.ps1
```

After installing the newly built launcher on a test TV, run the device contracts using the same signing identity as that installation:

```powershell
.\tools\Test-TV.ps1 -Serial '<TV-address:ADB-port>' -Adb '<path-to-adb.exe>'
```

That command installs a temporary instrumentation APK, checks provider decoding, symbols, input enumeration, worker PIDs/isolated UID, worker crash recovery, menus and actual focus visibility, then removes the test APK. It does not remove personal TV apps. Instrumentation terminates the target process; some TV firmware disables its accessibility binding afterwards. The script reopens Home and restores the Neo Home service only if it was enabled before testing, preserving unrelated services. Verify the remote Home button after a device test. Host tests cover navigation boundaries, categories, app-access intervals, PIN storage/expiry, companion ADB protocol and cleanup protection, and all CLI cancellation paths.

Weather API references: [Open-Meteo](https://open-meteo.com/en/docs), [MET Norway](https://api.met.no/weatherapi/locationforecast/2.0/documentation), [wttr.in JSON](https://github.com/chubin/wttr.in#json-output). These are data services; their implementation code is not included.

## Standby and dream recovery

The optional Home accessibility service records whether sleep began on Home or on an unrelated app. Screen-on/unlock events request a bounded Home return only for Home-origin sleep; normal apps keep their resume destination. Pending state survives service rebinds. Neo's system dream explicitly returns to Home when its recorded origin was Home. Keyguard/System UI overlays do not overwrite the origin.

The space player uses a native SurfaceView video plane. A dedicated decoder thread owns MediaPlayer calls; the UI reads cached playback state. Surface replacement recreates playback at its saved position. Exhaust frames are small cached software-composited sprites, avoiding per-frame GPU offscreen masking. Permanent disposal releases both playback and animation. Some TV screenshot tools omit the hardware video plane, so physical viewing is part of validation.

## Phone-to-TV installation

The installer is closed until the TV pairing screen opens it. An expiring QR carries a private-network endpoint, random bearer and self-signed TLS certificate fingerprint. The phone pins that fingerprint before sending credentials. Neo implements its bounded QR encoder/scanner and binary base/split transport in shared Java source. Camera images stay on the phone. A software JCA key in app-private storage avoids OEM AndroidKeyStore/Conscrypt signing incompatibility; the key is never transferred.

Uploads are size/path/hash checked and staged privately. The TV reviews each request, then Android PackageInstaller asks for native confirmation. Only its result callback is authoritative success. Existing system packages and core identities are excluded. Play requests open the official TV store listing after TV approval; store installation remains in Google Play. Closing the pairing screen cancels the receiver. See [remote apps](REMOTE-APPS.md) and [test procedure](REMOTE-TESTING.md).

## QR font import

The same pinned TLS receiver accepts a separate bounded `NEOF` font manifest and byte stream. Font-only pairing advertises its capabilities and rejects APK/store requests without requiring Android's unknown-apps permission. The receiver verifies size, SHA256, SFNT directory bounds/required tables and Android Typeface loading in the installer worker, then waits for local TV approval. Only approved fonts move into Neo's app-private, content-addressed library (20 entries maximum). The main-process pairing screen applies preferences, avoiding cross-process SharedPreferences writes. Home reloads selected typefaces when it resumes; clock digits fit their measured width. Fonts affect Neo only and are not installed into Android's font directory.

The phone uses Android's document picker and temporary app-private staging; no broad storage permission or font APK is required. Font selection, checks, transfer and TV result reuse the companion progress UI. [Android document access](https://developer.android.com/training/data-storage/shared/documents-files) and the [OpenType file specification](https://learn.microsoft.com/en-us/typography/opentype/spec/otff) describe the platform/file formats; no external implementation library is included.

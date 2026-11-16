# Neo runtime

The Version 1 runtime keeps the existing Neo behavior while retiring the unused inherited launcher implementation. The checkpoint before replacement is `checkpoint/pre-independent-runtime-20261116`; a local Git bundle also preserves it. Checkpoints and older releases remain historical records with their original notices.

## Modules and process boundaries

| Module | Responsibility | Boundary |
| --- | --- | --- |
| NeoHomeActivity | Glass home, categories, controls, dialogs and remote focus | Main application process |
| NeoCatalogService | Installed-app and icon-pack discovery | `:catalog` Binder worker |
| NeoWeatherService + NeoForecast | City lookup, location, provider adapters and cache | `:weather` Binder worker |
| NeoThemeService | Validate imported theme tokens | `:theme`, isolated UID, no network/files |
| NeoInputs | Read the Android TV hardware-input registry | Small platform adapter |
| NeoSymbols | Draw Neo control marks | Local Canvas rendering |
| NeoHomeService | Optional Home interception and access-window expiry | Accessibility service |
| NeoDreamService | System screensaver integration | Android DreamService |

This is an Android service architecture inspired by microkernel boundaries, not an operating-system microkernel. Catalog and weather have separate processes but share the application UID; theme validation additionally uses an isolated UID. UI requests are asynchronous with bounded timeouts. Worker death does not terminate Home; requests can reconnect.

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

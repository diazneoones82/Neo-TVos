# Build from source

Use Windows, JDK 17+, and Android SDK Platform/Build-Tools API 34 or newer. Android Studio's SDK Manager can install the SDK components.

1. Set `JAVA_HOME` to your JDK and `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to the SDK root.
2. Optional: set `TVMAP_BUILD_TOOLS` to a specific Build-Tools folder and `TVMAP_ANDROID_JAR` to a specific `android.jar`.
3. From the repository root, build the launcher:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\neo\build.ps1
```

4. Build the phone companion, which embeds that launcher APK and compiles the read-only TV label resolver:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\companion\build.ps1
```

Outputs:

- `neo/build/Neo-TVLauncher.apk`
- `companion/build/Neo-TVLauncher-Companion.apk`

A local development keystore is generated if absent. Keys/build output are ignored by Git. A locally generated key will not match the maintainer's release key, so locally signed builds cannot update a differently signed installed APK in place. Never commit a signing key or an ADB private key.

Run host checks without connecting to a TV:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\Test-Host.ps1
```

These include grid navigation, categories, PIN/timed access, ADB protocol/cleanup policy, and CLI cancellation mocks. Run `tools/Verify-Runtime.ps1` to check retired-file fingerprints and process boundaries. See `docs/ARCHITECTURE.md` for connected-TV instrumentation. Live USB transport still requires physical hardware.

## Cinematic media

The current runtime video is checked in under `neo/assets/neo/video/`. To regenerate it, install Python and `imageio-ffmpeg`, then run the scripts in `media-sources/` in this order: `render-cinematic-v2.py`, `render-cinematic-smooth.py`, `render-cinematic-stars.py`. They create intermediate files ignored by Git. Rendering can take several minutes. Asset prompts, provenance and credits are in `docs/VISUAL-CREDITS.md`.

Version 1 uses Android version codes 200 (TV) and 140 (phone) so it updates the previous releases without a downgrade or data reset. Both visible version names are `1`; package identifiers and signing identities are retained.

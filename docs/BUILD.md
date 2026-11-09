# Building AURA OS

AURA OS is built with the plain Android SDK tools: `aapt2`, `javac`, `d8`, `zipalign` and `apksigner`.
There is no Gradle and there are no dependencies to download.

## Requirements

| Tool | Version |
|---|---|
| JDK | 17 (`JAVA_HOME`, or `javac` on `PATH`) |
| Android SDK build-tools | 34.0.0 |
| Android SDK platform | android-34 (compile only; the app targets API 30 and runs on API 26+) |

```bash
sdkmanager "build-tools;34.0.0" "platforms;android-34"
export ANDROID_HOME=/path/to/android-sdk
```

## Debug build

```bash
./build.sh                  # Linux / macOS
```

```powershell
.\build.ps1                 # Windows; finds the SDK via ANDROID_HOME, ANDROID_SDK_ROOT or Android Studio's default
```

Output: `dist/AURA-OS-v<version>-debug.apk` and a `.sha256` file. The first run creates a throwaway debug
key in `keys/` (ignored by Git).

Install while you develop:

```powershell
.\build.ps1 -Install -Device 192.168.1.20:5555     # or set AURA_DEVICE
```

## Release build

Releases are signed with the AURA OS release key, which never goes into the repository or CI.
The maintainer keeps it offline and sets three environment variables before building:

| Variable | Meaning |
|---|---|
| `AURA_RELEASE_KEYSTORE` | path to the PKCS12 keystore |
| `AURA_RELEASE_KEY_ALIAS` | key alias |
| `AURA_RELEASE_STORE_PASS` | keystore and key password |

```bash
./build.sh --release        # or .\build.ps1 -Release
```

Output: `dist/AURA-OS-v<version>.apk` plus its SHA-256. The scripts pass the password to `apksigner` through
an environment variable, never on the command line.

Forks must use their own key; Android refuses an update signed by a different key.

## Versioning

`versionName` and `versionCode` live in `app/AndroidManifest.xml` and are the single source of truth.
Tags are `v<versionName>`, for example `v1.1.0-beta.1`. `versionCode` is `MAJOR·1000 + MINOR·100 + PATCH·10 +
pre-release number`, so `1.1.0-beta.1` is `1101`.

## Reproducibility

Both scripts write the APK entries in a fixed order with a fixed timestamp, so the unsigned APK is the same
for the same source and tool versions. The signature then depends only on the key. To compare two builds,
compare the entries outside `META-INF/`.

## Continuous integration

`.github/workflows/build.yml` builds a debug APK on every push and pull request on Ubuntu with JDK 17 and
build-tools 34.0.0, verifies the signature and uploads the APK as a workflow artifact.

## Project layout

```
app/
  AndroidManifest.xml
  src/io/github/saksen2k1/aura/   Java sources (framework only)
  res/                            layouts are built in code; values/ (English), values-da/ (Danish)
  assets/wallpapers/              12 CC BY-SA photos, see CREDITS.md
build.sh, build.ps1               build scripts
docs/                             guides and screenshots
```

### Windows note

`aapt2 link -A` stores asset paths with backslashes on Windows (`assets/wallpapers\01.jpg`), which Android
cannot open. The build scripts add the assets to the APK themselves with forward slashes.

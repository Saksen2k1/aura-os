# Changelog

All notable changes to AURA OS. Versions follow [semantic versioning](https://semver.org/).

## [1.1.0-beta.1] - 2026-11-09

First public release.

### Added
- English interface (default) with Danish translation; dates and times follow the TV's language.
- Runs on Android TV / Google TV 8.0 (API 26) and newer.
- Opens as a normal TV app from the app list; becoming the home screen is optional.
- Settings → **Usage access** and **Default home app** shortcuts to the system screens.
- Photographer and licence shown under every built-in wallpaper.
- Debug and release build scripts for Windows, Linux and macOS, with fixed APK entry timestamps.
- GitHub Actions build, issue templates, install, build, compatibility, troubleshooting and performance docs.

### Changed
- Own application ID `io.github.saksen2k1.aura`, so it installs next to other launchers.
- Neutral first-run app row of common TV apps; if none are installed, the first eight installed apps.
- Inputs button only on devices that report TV inputs or have a tuner app.
- Live TV app chosen from a list of known tuner apps instead of one fixed package.

### Removed
- Network permission and the Watch Next query (third-party launchers cannot read Watch Next).
- Location permission.

### Fixed
- Top-right buttons: focus could jump back to the previous button on Android 14.

## 1.0 - private

The private build that 1.1 grew from: cinematic layout, dark cards with colourful logos, Recently used,
settings, app order and all-apps grid, on one Android TV 11 set.

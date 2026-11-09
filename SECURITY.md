# Security policy

## Supported versions

Only the latest release receives fixes.

## Reporting a vulnerability

Please report security problems privately through GitHub:
**Security → Report a vulnerability** on this repository (private vulnerability reporting).
Do not open a public issue for them. You can expect an answer within a week.

## What AURA OS does and does not do

- Requests no network permission and sends no data anywhere.
- Requests `PACKAGE_USAGE_STATS`, which does nothing until the user allows usage access; the history stays on
  the TV and is used only for the Recently used row.
- Exports only its main activity (needed to be a launcher); settings screens are private to the app.
- Stores its settings in the app's private storage and reads user wallpapers only from its own
  app-specific folder.

## Verifying a download

Each release lists the APK's SHA-256. Release APKs are signed with the AURA OS release key, certificate
SHA-256 `af99d1a0c4710225cadf670450c6fd5b07e5c32fecfccf58e03255be1baa7def`:

```bash
apksigner verify --print-certs AURA-OS-v1.1.0-beta.1.apk
```

An APK with a different certificate did not come from this project's releases.

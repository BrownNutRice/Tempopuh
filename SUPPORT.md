# Support

Use [this fork's issues](https://github.com/matchalattewithbanana/Tempo/issues) for bugs and proposals. Search existing issues first. This project does not publish a guaranteed support schedule.

## Useful bug reports

Include the installed app version/version code, Android version/device model, affected player or import provider, concise reproduction steps, expected/actual behavior and whether the issue persists after reopening the screen. For UI issues include preset/tone, font scale and a redacted screenshot. For timing issues give observed play/pause/seek times rather than only a total.

Review diagnostics before sharing. Never post an OAuth token, signing key, pairing token, full backup or unredacted private listening history.

## Common checks

- **Tracking stops:** review notification access, enabled/blocked apps and background protection; see [tracking accuracy](docs/TRACKING_AND_IMPORT_ACCURACY.md).
- **Now Playing differs from totals:** presence is provisional; history updates after a listen is finalized and persisted.
- **Google sign-in fails:** check the exact APK signer and OAuth configuration; see [Drive setup](docs/GOOGLE_DRIVE_SETUP.md).
- **Update cannot install:** compare signing identity and version code. Export data before any uninstall.
- **Light text is unreadable:** report the screen/preset/tone; full Light support remains in the audit backlog.
- **Import count differs:** review export limitations and [history quality](docs/HISTORY_DATA_QUALITY.md).

Release notes and open issues describe known beta limitations. A successful build is not evidence that every device scenario passes.

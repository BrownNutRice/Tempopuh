# Changelog

Fork-specific changes. Published tags and artifacts are listed in the [release catalog](docs/releases/README.md). Inherited upstream history is preserved separately in the [archive](docs/archive/UPSTREAM_CHANGELOG.md).

## Unreleased

### Fixed

- Automatic local backup retention now records per-document creation order and always protects the verified destination. Undated legacy files are retained conservatively instead of sorted by random UUID.
- Backup restore stages artwork privately, validates the archive before promotion and uses a unique destination namespace. Rejected archives and transaction-body failures cannot overwrite existing artwork.

### Maintenance

- Reorganized project, developer, support and release documentation around the maintained fork.
- Corrected privacy/storage/reporting declarations and in-app privacy descriptions.
- Added architecture guidance, contribution/report templates and a release catalog.
- Restored manual-only beta workflow triggers and repaired the companion build-all command.

Audit proposals remain open issues; they are not listed as shipped fixes. No APK is built or published by this maintenance entry.

## [4.9.0-beta.1] — 2026-11-17

**Reliability and import beta.** Starts the SemVer release series; Android version code 4818.

### Fixed
- Preserved unsynced browser plays during pruning and durable queued plays during offline recovery; replay now retries bounded due batches without per-event sleeps.
- Removed blocking Room reads from notification-listener startup and prevented routine maintenance from pruning listening history.
- Made Spotify credentials fail closed on secure-store errors; made migration snapshots verify copies and analytics queue writes atomic.
- Rejected unsigned, malformed and replayed WebSocket control frames, withheld pairing tokens from the WebSocket handshake, and removed the extension's global host grant.
- Bounded backup/Spotify/Musicolet import memory and disk usage; Spotify history is staged to disk and replayed in small batches.
- Corrected Home unique-track semantics, calendar invalidation and explicit session-start day attribution; gated decorative animation for reduced motion.

### Validation
- Browser extension typecheck and smoke checks passed locally.
- Published by successful [beta workflow run 44112413379](https://github.com/matchalattewithbanana/Tempo/actions/runs/44112413379) at tagged commit `289fcd0`; its gates included unit tests, lint, APK assembly, emulator tests and extension checks. Earlier local Android validation was unavailable.
- Physical-device visual, accessibility, playback and battery checks remain beta feedback items.

## [4.8.16-fork.2-beta.1] — 2026-11-17

**Appearance Studio beta.** Six curated looks, experimental Light/System, OLED,
typography and accessibility preferences, layout controls and saved looks.

### Added
- Abyss, Obsidian Gold, Aurora, Glacier, Ember and Solstice themes, with OLED
  and opt-in Light/Follow System beta tones.
- Typography, bold text, density, corner and finish options; optional wallpaper
  and cache-only current-artwork accents; adaptive effects and reduce motion.
- Home layout preferences, opening Stats filters, and a compact playback strip
  with thumbnail artwork and stale-status handling.
- Named appearance looks and bounded appearance-only JSON import/export.

### Improved
- Semantic text, surface and accent colors across navigation, history, stats,
  settings, backup/import screens, dialogs and shared components.
- Prevented duplicate Now Playing UI and reserved safe scroll space.

### Known limitations
- Debug signing may differ from prior APKs; export data before reinstalling.
- Light/System still need physical-device visual, large-font and accessibility feedback.
- Google OAuth/Drive backup requires correct per-signer configuration.
- GPU-heavy progressive blur/refraction and rebranding are not included.

## [4.8.16-fork.1] — 2026-11-16

**First fork preview** of Tempo with more responsive local music tracking and
safer reconciliation between Android, Spotify and the browser companion.

### Added
- Provisional live-session listening timer and privacy-conscious playback timing diagnostics.
- Opt-in, backup-confirmed history-quality scan and correction for unverifiable
  legacy Spotify API estimates.
- Spotify recently-played page traversal with safeguards against missing pages
  and incorrect cursor advancement.
- Regression coverage for replay protection, Spotify metadata validation, fast
  repeat listening, concurrent devices and historical session aggregation.

### Changed
- Improved elapsed-time accounting across pause/resume, seeking and screen-off playback.
- Reduced completed-play queueing latency and refresh delays in Home, History and Stats.
- Changed Spotify API history reconciliation to approximately 20-minute
  WorkManager scheduling; removed the 45-second foreground playback-state poll.
- Corrected average/longest session calculations and unknown-completion/skip statistics.
- Reorganized the README and removed checked-in generated browser dependencies.

### Fixed and hardened
- Centralized listening-event deduplication across local and imported sources.
- Hardened Spotify broadcast validation and browser companion HTTP request replay protection.
- Corrected stalled/retryable HTTP response handling, importer concurrency and track identity conflicts.

### Known limitations
- Debug-signed preview APK; in-place upgrades require the same signing certificate.
- Google Drive requires per-fork OAuth configuration and actual APK signing SHA-1.
- Spotify history availability is constrained by Spotify's own server-side window.
- Real-device verification of the earlier one-minute-per-song symptom is still pending.

Read the [complete release notes](docs/releases/v4.8.16-fork.1.md).

---

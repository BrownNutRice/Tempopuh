# Breadcrumbs — proposed Tempo rebrand plan

**Status:** Planning only. No product naming, icons, Android IDs, OAuth credentials or published assets have been renamed.

**Product concept:** **Breadcrumbs — Your listening story, remembered.** A private, local-first journal of tracks, artists, moments and music habits across Android and browser playback.

## Phase 0 — Rights, name and distribution gates (BLOCKING)

1. **Copyright authorization:** The repository's [LICENSE](../LICENSE) says "NO REBRANDING / WHITE-LABELING" and prohibits distribution of rebranded derivatives without permission. This fork is based on Avinash's Tempo. **Secure explicit written permission from the applicable rights holder(s), including authorization for the proposed branding, distribution and use of existing logos/assets, before making or publishing the Breadcrumbs rebrand.** Do not remove required credits or notices. If permission is unavailable, this rebrand cannot proceed on this codebase; a separately and properly licensed implementation would be a distinct undertaking.
2. **Trademark and marketplace clearance:** Conduct a proper country-by-country trademark search for "Breadcrumbs" in software/music-related classes, including India and distribution markets, and check the Google Play, Apple App Store, Chrome Web Store, Firefox Add-ons, GitHub, domains and social handles. Initial public search already found unrelated products using "Breadcrumbs": [Android example](https://play.google.com/store/apps/details?id=com.mycompany.breadcrumbsmobileapp), [iOS example](https://apps.apple.com/us/app/breadcrumbs-location-history/id1503184529), and [another app with music-history functionality](https://breadcrumbs.bar/). **This is not a legal clearance** or a finding that a software/music mark is available.
3. **Product/go-to-market scope:** The existing custom license also forbids commercial use. Any monetization, subscriptions, donations or commercial distribution need separate rights clearance before product planning.

**Decision gate:** Approve "Breadcrumbs" only after written rights authorization and a satisfactory name/mark clearance. Until then use Tempo in distributed binaries and marketing.

## Phase 1 — Brand and design system (after gate)

- Positioning: personal listening journal, not a streaming client or GPS breadcrumb tracker. Explain the distinction prominently to avoid confusion with other apps.
- Proposed brand language: "Leave a trail of music", "Your listening story, remembered"; verify suitability and distinctiveness before adopting.
- Develop 2–3 original icon concepts: a winding path of note-shaped trail markers, a trail of album tiles, and an abstract sound-wave path. Test legibility at 48 dp, adaptive icon masks, Android monochrome icon and notification small icon. Avoid borrowing existing logos.
- Select accessible type hierarchy and color tokens for light/dark/dynamic color, contrast meeting WCAG AA, reduced-motion support and 320 dp small screens.
- Audit every branded surface: onboarding, main navigation, Now Playing, statistics/share images, Spotlight, charts, widgets, notifications, settings, backup screens, permission rationale screens and splash screen.
- Prepare localization updates for `values`, `values-de`, `values-fr`, `values-hu`, `values-pt` and `values-ru`; avoid machine-renaming "Tempo" where it means musical tempo rather than the product.

## Phase 2 — Android implementation and continuity

**Safest choice:** Keep `applicationId = "me.avinas.tempo"` and `namespace = "me.avinas.tempo"`. Rename the **visible product**, not the installed application identity. Google Play treats an application-ID change as a distinct app, not an upgrade; keeping the same ID and signing certificate preserves update/data continuity. See [Android application-ID guidance](https://developer.android.com/build/configure-app-module).

- Update `AndroidManifest.xml` application `android:label` and launcher/splash app icons; resource `app_name`; user-facing strings, Compose literals, notifications and accessibility labels. Use a centralized brand resource rather than new scattered hardcoded strings.
- Update app shortcuts, widget labels and assets, default share cards, exports, support contacts, package descriptions and in-app help. Keep functional statistic labels such as "average tempo" unchanged.
- Preserve Room DB file names, schema IDs, migration paths, SharedPreferences keys, backup aliases, SAF document selection, WorkManager unique work names and legacy export/import format IDs. Existing history should survive an in-place APK update unchanged.
- Preserve `tempo://spotify-callback` while current Spotify integration relies on it; a display-name change does not require changing the redirect scheme. Only migrate OAuth redirect schemes with coordinated provider configuration, support for the old redirect and real-device sign-in tests.
- Preserve `${applicationId}.provider` FileProvider authorities and notification-listener component class names unless an explicit backward-compatibility migration is tested.
- **Do not** rename the Kotlin `me.avinas.tempo` packages merely for cosmetic reasons. The namespace is an implementation detail; renaming it increases crash and reflection risks without improving the user-visible brand.
- If a new application ID is unavoidable, treat it as a separate app requiring explicit export/import migration: Android does not automatically transfer private app data across package names, notification access and OAuth registrations must be re-granted, and old installs do not update in place.

## Phase 3 — Connected integrations and companion releases

- **Google Sign-in:** Review the Google Cloud OAuth consent screen, Android package + signing SHA-1/SHA-256 client registration, Web client ID / Drive scope, Google Drive backup naming, existing backup restore compatibility, and debug vs release signing. Coordinate any visible app-name update with the OAuth consent screen and privacy policy. Preserve old backups as readable.
- **Spotify:** Update the connected-app name/website/branding only with the registered client owner; verify redirect URI `tempo://spotify-callback`, user scopes and device-broadcast permission screen. Avoid simultaneous package and OAuth scheme changes.
- **Browser extension:** Rename extension manifest title and description, popup branding, icons, package name, Chrome/Firefox store copy and source/dist bundles. Preserve existing extension IDs where the stores allow an update. Keep the local network pairing protocol and old Tempo-client pairing compatible for at least one transition release; don't rename wire message fields or pairing secrets for aesthetics.
- **Local server/desktop links:** Update QR pairing copy, discovery names and downloadable companion labels. Avoid breaking already paired devices and avoid reinterpreting `tempo` protocol strings as display text.
- **Legal and privacy:** Review privacy disclosures, permissions, telemetry opt-ins, support URLs, provenance/credits, license/notice obligations, data safety disclosures, required OSS acknowledgements and the distinction between optional Drive backup and local-first playback history.

## Phase 4 — Repository, docs and release train

- Prepare a migration PR with localized strings/graphics separated from authentication, DB or business logic changes. Add screenshot diffs and an inventory of every deliberate "Tempo" string still left for compatibility.
- Only after rights clearance, update the repository display name, README, docs, screenshots, issue templates, support contact, changelog and versioning conventions; preserve GitHub redirects or an explicit transition notice.
- Use a new `versionCode` greater than the existing `4816`. Maintain signing certificate continuity. Mark the first authorized Breadcrumbs build as a **rebrand of the existing Tempo fork** in changelog and application UI.
- Build debug APK then signed preview, staged rollout and recovery/rollback instructions. Keep published Tempo installer and checksum available where license/distribution terms permit. No automatic release publication.
- Ensure any release artifacts and screenshots are genuinely rebranded, while internal compatibility identifiers remain stable.

## Phase 5 — Required acceptance testing

- Upgrade **over** a real Tempo installation with historical tracks, artwork, playlists, settings and statistics; verify no data resets and no regrant required for notification access.
- Test notification listener, battery protection screens, foreground notification, widgets, boot recovery, local MediaSession artwork, and time accounting on Android 8–16.
- Exercise Spotify OAuth login/reconnect, API verification, Google Sign-in, Drive backup/restore including old naming, and imports/exports from legacy versions.
- Pair old and updated browser/desktop companions with old and updated Android installations; verify QR scanning, token rotation, HTTPS/HMAC transport risks and compatibility.
- Validate translations, accessibility contrast, 320 dp layouts, talkback art description and screenshots. Test restore from backup before staged deployment.
- Document rollback limitations: once data/schema migrations and rebrand-specific backups roll forward, restore an older APK only with a tested compatible export.

## Performance work running in parallel

The separate performance branch `feat/now-playing-artwork-performance-2026-11-17` adds a bounded-size cover thumbnail, MediaSession cover URI preference and cached Room artwork observation, plus a lifecycle-aware once-per-second progress subtree. It does **not** perform the rebrand and can ship independently after Android tests.

Next performance validation: measure Home/Profile/Settings recomposition count, cold-start latency, artwork load latency and memory footprint, scroll jank, idle vs playing battery use and Room query frequency. Use Android Studio Macrobenchmark/Baseline Profiles and a production-like profile; do not claim a battery or latency percentage without device measurements.

**Prepared:** 2026-11-17. **Launch authorization:** pending rights and naming clearance.

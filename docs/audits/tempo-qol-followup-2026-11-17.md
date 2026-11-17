# Tempo — usability and diagnostics follow-up

**Date:** 2026-11-17  
**Status:** Committed to `main`. Static review only; no tests/builds/CI/releases dispatched.  
**Naming:** Tempo unchanged. Breadcrumbs rebrand postponed.

## Delivered in this pass

1. **Tracking overview:** Settings now reads `TrackingServiceHeartbeat` only while the overview is visible and STARTED, refreshing every 30 seconds. It distinguishes listener permission, disconnected state, stale heartbeat and recently connected listener. Last media/notification callback age is exposed as a signal only, **not proof a listen was saved**. No titles, artists, paths or credentials appear in these statuses.
2. **Diagnostics export:** Existing user-initiated, local-only diagnostic report now includes separate notification and MediaSession callback ages. The schema still holds no listening-content fields. Pure JUnit tests cover heartbeat-state transitions and diagnostic formatting.
3. **Drive backup visibility:** Settings overview reads `BackupSettingsManager.settings` (DataStore) with a lifecycle-aware subscriber and links to Backup & Restore. Shows whether Drive backup is disabled, latest Drive attempt state, and the most recent successful Drive backup timestamp. The backup screen shows the same status alongside the manual Drive action.
4. **Honest status scope:** `BackupSettingsManager.updateLastBackup` is called by `DriveBackupWorker`; `LocalBackupWorker` does **not** update that status. All copy explicitly scopes the status to **Google Drive**. A separate local-backup outcome is still needed.
5. **Home density preference:** The optional extended discovery cards are now remembered in the existing app DataStore. Closing them immediately clears expensive cards and cancels an optional query burst. Previously unconditional discovery and most-active-hour fetches are avoided in compact mode.
6. **Accessibility:** The History filter action advertises the right TalkBack label and the search-clear touch target grows from 36dp to 48dp. New copy is available in English, German, French, Hungarian, Portuguese and Russian.

## Implementation evidence

- `ui/settings/TrackingHealthOverview.kt` and `TrackingHealthOverviewTest.kt` — pure freshness mapping and boundary tests
- `ui/settings/SettingsScreen.kt`, `SettingsViewModel.kt`, `BackupRestoreScreen.kt` — user-facing status controls
- `data/diagnostics/DiagnosticsReport.kt`, `ui/settings/DiagnosticsViewModel.kt`, and `DiagnosticsReportTest.kt` — report extensions
- `ui/home/HomeScreen.kt` and `HomeViewModel.kt` — persistent density selection and lower-cost compact dashboard
- `ui/history/HistoryScreen.kt` — accessible filter and clear controls
- Resource strings for all six supported locale folders

## Important boundaries / next work

**Do not assume compilation or device validation.** Tests were added but not run, by user request. Code and strings were inspected statically. Validate when explicitly authorized to build.

- **Local backup health:** Add a *separate* persisted last-attempt time/status for `LocalBackupWorker`, with an interrupted-run recovery policy. Do not conflate it with the existing Drive-only state; test SAF permission revocation and archive integrity.
- **History Undo:** Plan a durable, transaction-safe pending-delete record to retain deleted event data and associated track references through process death. The current confirmation dialog should remain until safe Undo works. Avoid non-durable `delay(5000)` deletion or UI-only restoration.
- **Desktop transport:** See [desktop sync v4 proposal](../desktop-sync-v4-security-plan.md). LAN HTTP still exposes a reusable Bearer token. Do not change the wire protocol in only the Android app or only the browser extension.
- **Metadata correction:** Add a reusable editor linked from History and Song Details only after identifying canonical IDs and testing rename/merge history with preserved listening records.
- **Accessibility:** Audit font scaling, TalkBack focus order, status announcements and reduced-motion behavior across Spotlight and Profile animations.
- **Baseline Profiles/benchmarks:** Instrument cold launch, Home list scroll, import pagination, tracking battery and disk usage on an emulator/physical device. Never quote an estimated speed-up as measured.

## Acceptance checklist for future authorized test build

- [ ] Launch with a fresh and a populated database; verify Settings overview and all five categories
- [ ] Validate permission off, listener disconnected, listener healthy and heartbeat stale cases without reporting saved plays
- [ ] Check export contains callback age but not track titles / artists / files
- [ ] Validate Drive disabled, never backed up, failed, running and successful states, and last success timestamp
- [ ] Confirm the separate device-folder backup still works and is not accidentally labelled by Drive state
- [ ] Restart with expanded Home selected: it stays expanded; collapse it, restart again: remains compact
- [ ] Test frequent playback events while on compact Home: no secondary ranking query burst
- [ ] Test TalkBack History filter and clear-search target, locale XML formatting and 320dp widths
- [ ] Confirm unit tests, Android emulator tests, debug APK assemble, lint, and preservation of all historical records

**Build policy:** `docs/BUILD_AND_RELEASE_POLICY.md` is unchanged; GitHub CI and APK workflows stay dispatch-only, no automatic publishing.

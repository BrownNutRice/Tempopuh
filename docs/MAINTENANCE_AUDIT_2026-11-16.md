# Tempo maintenance audit — 16 November 2026

Scope: static inspection of the fork at `matchalattewithbanana/Tempo`, following
the unbuilt tracking revisions. This document is an engineering handoff, not a
claim of device, build, lint, migration or automated-test verification.

**Build policy for this maintenance pass:** No CI or APK workflow was triggered.
Every pushed code change used `[skip ci]`. Run the usual Android unit tests,
instrumented tests, lint and a physical-device acceptance pass before release.

## Implemented in the maintenance pass

| Area | Change | Intended effect |
| --- | --- | --- |
| Listening source authority | Local writes now go through the same Room deduplication transaction used for API imports | API-first and local-first arrival order should produce one play, with local observed listening duration preferred |
| Persistence | Batch/offline queues treat a dedup result as already saved, not a database write failure | Avoid retrying legitimate duplicates indefinitely |
| Completion data | Unknown, non-skipped import records are excluded from skip/average-completion calculations; desktop records without known track length stop assuming 90% | Prevent unknown evidence from degrading skip rates and quality scoring |
| Session statistics | Observed end timestamps, 20-minute grouping and counted active listening time replace average per active day and wall-time longest-session estimates | Correctly name and compute session lengths |
| Source identity | Reject conflicting secondary provider metadata; never return a track_id of -1 after an ignored insert | Protect track matches and foreign-key consistency |
| Spotify API | Periodic sync set to 20 minutes, existing jobs upgraded once; foreground 45-second API loop removed | Keep on-device broadcasts and MediaSession authoritative |
| Spotify import | Serializes manual + periodic import; handles cancellation and permanent auth failures | Avoid overlapping import writes and repeated unproductive retries |
| Presence | Atomic StateFlow merging between device, MediaSession and optional API snapshots | Reduce callback races and stale overwrite |
| Android clock | Live listening stopwatch uses elapsedRealtime to include CPU suspend/offloaded playback | Avoid undercounting while the device is asleep |
| HTTP retry | Never return a closed response; do not block for huge Retry-After; retry only idempotent requests | Preserve useful errors and avoid stalled requests |
| Repository | Professional README, updated attribution, remove tracked generated node_modules | Improve onboarding and reduce repository clutter |
| Tests | Add Android Room order/reconciliation coverage and pure Kotlin session aggregation coverage | Catch regressions once tests are run |

## Outstanding concerns, requiring dedicated verification

1. **The previous APK's one-minute symptom** cannot be fully diagnosed from
   source alone. Compare a single Spotify play's broadcast positions, media
   state transitions, elapsed clock, saved `playDuration` and displayed value.
   Do not mark this fixed without device evidence.
2. **Spotify recently played** returns at most 50 items per request. Extended
   offline gaps and Spotify's own accessible history limit may leave gaps.
   An API import has no observed listening duration; avoid inventing one.
3. **Mixed-source duplicate policy** must distinguish the same physical play
   from legitimately simultaneous plays on multiple devices, and must handle
   repetitive short tracks near the timestamp tolerance.
4. **Completion is legacy non-null**. Unknown completion currently uses a 0%
   storage sentinel plus provenance-aware SQL. A future versioned schema should
   allow explicit NULL completion and migrate rows safely. Review every
   downstream quality and badge calculation at the same time.
5. **Android broadcast authenticity** on pre-Android-14 devices cannot use the
   sender UID API exposed on newer devices. Treat playback broadcasts as
   untrusted observations, not authorization or a billing/security event.
6. **Historical event repair** is not performed automatically. Earlier
   imports may contain guessed durations/completion and earlier duplicate rows;
   prefer an opt-in diagnostic and safe, backed-up reconciliation tool.
7. **Real device coverage** should include Android 8-16, screen-off/offloaded
   Spotify playback, missing playback positions, notification-only players,
   Bluetooth pause/resume, 2x speed, seeks, replays, phone reboot, app kill,
   low battery, and concurrent Spotify Connect on another device.
8. **Google Drive OAuth** still needs app-specific Google Cloud setup, a Web
   client ID, and an Android credential registered to the exact APK signing
   key SHA-1. Never embed an upstream key or imply Google sign-in works in an
   unconfigured fork build.
9. **Browser companion** needs independent LAN security and stress tests:
   replay protection, input size limits, pairing rotation, simultaneous web
   sources and offline queue replay.
10. **Disabled/legacy Spotify enrichment and reconstruction paths** need an
    intentional product decision before removal; deletion without tracing
    dependency injection and historical users may regress builds.

## Acceptance criteria before a new APK release

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` passes.
- `./gradlew :app:connectedDebugAndroidTest` passes on an Android emulator.
- A real Spotify song listened to for three minutes stores approximately
  three minutes **excluding pauses**; seek forward must not invent listening
  seconds. Repeat with screen off and Bluetooth output.
- API-first and local-first versions of the same play yield one listening
  event; the local observation supplies accurate duration, and the event
  appears promptly in Home/History/Stats.
- Imported unknown completion is not counted as a skipped song or a known
  zero-percent completion.
- A manual and periodic Spotify sync overlap without duplicate tracks,
  duplicate sessions or cursor regression.
- Google Drive backup/restore is verified only after a configured and
  consistently signed build, and only on backed-up test data.

The original, older engineering findings remain in
[`docs/PERFORMANCE_AUDIT.md`](PERFORMANCE_AUDIT.md).

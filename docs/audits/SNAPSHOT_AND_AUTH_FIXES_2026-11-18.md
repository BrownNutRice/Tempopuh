# Consistent backups and Spotify account invalidation — 18 November 2026

Scope: issues [#42](https://github.com/matchalattewithbanana/Tempo/issues/42) and [#43](https://github.com/matchalattewithbanana/Tempo/issues/43), based on main `2facce1`. No APK includes this batch yet.

## Database export snapshot

`BackupSnapshotWriter` reads every exported Room table and streams event/archive pages into a private JSON file in one `database.withTransaction` block. ID ceilings remain paging bounds; they are no longer described as a sufficient snapshot on their own. Schema foreign keys are checked with `PRAGMA foreign_key_check`, and every streamed event must reference an exported track. A malformed source graph aborts export rather than silently dropping events during a future restore.

`ImportExportManager` owns the temporary JSON lifecycle and deletes it in `finally`. ZIP compression, image copying and destination upload/copy happen after the database transaction ends. A failed/cancelled archive is removed. An image that disappears or fails during packaging aborts the operation instead of leaving a successful archive with a misleading manifest.

Room serializes concurrent database writers while JSON is staged. JSON serialization and its temporary disk writes are still inside the transaction; large-library transaction duration must be measured on devices. This is a consistency fix, not a claim of measured performance improvement. Profile identity comes from a separately captured DataStore value; image bytes are copied afterward. Neither is claimed to be transactionally atomic with Room.

`BackupSnapshotIntegrationTest` pauses production snapshot writing after the bounded tables are read, starts a competing merge, resumes event paging and checks that exported events still reference the original exported tracks while the live database subsequently reflects the merge. This instrumentation test has been added but not executed here.

## Spotify account invalidation

`AccountSessionGate` binds token requests and their storage/state commits to a generation. Disconnect, new login and client-ID changes advance that generation before changing local credentials or pending authentication state. Obsolete registered OkHttp calls are cancelled outside the local-state critical section, without awaiting network completion. Token requests use asynchronous OkHttp callbacks and cancel their call when the waiting coroutine is cancelled. Responses are closed after consumption.

Successful and failed refresh/exchange responses can mutate local storage/state only while their generation remains current. An old 400/401 cannot disconnect a newer account. Authorization callbacks must match a current, single-use persisted state; superseded callbacks cannot clear a newer attempt. Settings now routes client-ID changes through the auth manager. Token-response bodies are no longer included in failure logs.

The existing secure-storage failure behavior remains relevant: this does not certify Keystore availability, external OAuth revocation or erasure of already-issued server tokens.

## Validation and limits

- Fourteen JUnit 4.13.2 tests passed using Kotlin 2.2.10 independently of Gradle: eight backup/artwork regressions plus six account-generation/concurrency tests.
- Account tests cover delayed refresh/exchange success after disconnect, obsolete failures, client changes before call registration, a concurrent commit/disconnect, cleanup failure and completed-call removal.
- Targeted compilation checks cover the actual Spotify auth/storage/API/model sources using Android framework classes, current declared HTTP/coroutine dependencies and a temporary generated-BuildConfig substitute. This is not a full Gradle/KSP/application build.
- Android instrumentation coverage was added for the Room merge barrier and four delayed/cancelled/superseded Spotify auth cases. These tests have not run on an emulator/device.
- `git diff --check` passed. The Gradle distribution download remains unavailable in this environment; full app compilation, lint and Android integration execution remain pending.
- No CI dispatch, APK assembly or release publication was performed.

Next data-safety issue: [#44](https://github.com/matchalattewithbanana/Tempo/issues/44), coordinating full deletion with active producers and pending recovery state. Other audit findings remain open; this batch does not implement them.

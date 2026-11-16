# Listening-history data quality and safe repair

This guide covers the history-quality report in **Settings → Your Data →
Check listening-history accuracy**. No history is edited when you open the report.

## Why Spotify API minutes can be wrong

Spotify's `/me/player/recently-played` response identifies *which* track was
played and *when* it was played. It does **not** report exact listening duration,
seek count or completion percentage. Older versions of some music trackers,
including early Tempo import paths, guessed values from catalog song length.

Tempo preserves a play record (track + play time) but should not count those
guesses as observed listening minutes. Independently observed Android playback
and exported extended streaming history can have real listening duration.

## Checking legacy events

The scan reports:

- **Unverifiable Spotify API estimates:** records with source exactly
  `com.spotify.music.import` and a duration, completion or skipped flag that
  the recently-played endpoint could not have supplied.
- **Reconstructed history:** lower-fidelity inferred events created by older
  features. They are **not** automatically changed or deleted.

## Optional correction

1. **Export and verify a Tempo backup first.** Keep that file somewhere safe.
2. Open **Settings → Your Data → Check listening-history accuracy**.
3. Read the number of affected API records. Nothing changes at this stage.
4. Explicitly confirm that a backup exists and choose **Correct API estimates**.

For the exact `com.spotify.music.import` source, the correction:

- keeps each event ID, track reference, timestamp and source;
- resets invented `playDuration` to zero and completion to *unknown* (legacy
  schema stores `0`), and clears unsupported skip flags;
- invalidates statistics caches to refresh listening totals;
- does **not** remove imported plays or silently repair any other source.

Completion is not globally made nullable in this release because a Room schema
change requires new migration tests and would change multiple analytics and
backup formats. Instead, statistics queries exclude unknown non-skipped imported
completion values. A future schema migration should add a dedicated nullable
completion field, after testing all existing imports.

The correction may reduce previously inflated listening minutes. This is
expected because an API timestamp alone is not proof of minutes listened.

## Spotify history window

Spotify limits a single recently-played page to 50 tracks. Tempo now attempts
bounded backwards pagination, stopping when it reaches the previously saved
cursor (with a brief overlap for reconciliation). It never advances that cursor
after an incomplete fetch. However, Spotify may expose only a limited history
window and can reject or omit older pages; this cannot be fixed from a third-
party app. For complete historical data, use the Spotify extended streaming-
history JSON export and preserve its observed milliseconds.

Automatic reconciliation is scheduled around **every 20 minutes** through
Android WorkManager, not as a guaranteed timer.

## Verifying the original one-minute symptom

The historical APK and source revisions use different timing algorithms.
The modern service uses elapsed real time while playback is confirmed and
separate media-position coverage for completion. Verification still requires a
device: play a song for 3 minutes, pause for 30 seconds, resume for 1 minute and
then change tracks; compare **raw stored milliseconds** against the elapsed
listening time (about four minutes), not only rounded UI labels.

Do not export private OAuth tokens or device identifiers with diagnostic logs.

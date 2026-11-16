# Tempo live tracking — latency and accuracy changes

The app tracks **two different facts**:

- **Now Playing / this session** — live, provisional UI values. These should
  respond to playback immediately, even before a database row exists.
- **History / Home totals / charts** — durable, finalized listening events.
  The row is committed when a session ends or a track changes and meets the
  configured minimum-play rule. The current track must **not** be added to
  daily listening minutes as a finalized play before it ends.

## Pipeline

1. Spotify Device Broadcast Status, Android MediaSession and notification
   listeners update the shared local Now Playing presence. Recent Spotify
   broadcasts briefly take precedence over stale MediaSession snapshots.
2. MediaSession positions are extrapolated using `PlaybackState.lastPositionUpdateTime`
   and Android's monotonic clock. A stale position value no longer freezes
   estimated track progress.
3. `PlaybackSession` uses a **monotonic playing-time stopwatch** (excluding
   reported pauses) for real seconds listened. Media position advancement is
   tracked separately for seek-aware *track completion*. This avoids inflating
   listening minutes at 2x speed or treating a large seek as time listened.
   When no position is available, notification-only playback still accumulates
   eligible listening time.
4. When a session finalizes, `MusicTrackingManager` groups completed events for
   **up to 750 ms**, rather than 30 seconds, before saving them through Room.
   An idle queue consumes no periodic wakeups.
5. Only **after the Room write succeeds**, the manager invalidates cached stats
   and signals active views. Failed writes enter the existing durable retry
   queue. Home/History/Stats coalesce refreshes for ~300–350 ms to avoid double
   querying the same event.
6. Spotify API's *recently played* feed is separate: WorkManager pulls it
   approximately every 20 minutes and reconciles duplicates against live tracking. Imported
   histories refresh statistics when their writes complete. API data may arrive
   late on Spotify's servers and does not contain observed listening duration.

## Important interpretation notes

- The Live Now Playing card now shows **Listening this session**, a continuously
  updated *provisional* number. It is not added to saved charts until it
  satisfies the counting rules and is actually persisted.
- Track completion is measured from observed media progression when a reliable
  duration is known. An unknown duration or missing position evidence has
  completion 0 in the legacy database, meaning *unknown* — not a verified skip.
- Play/pause transitions go through `pause()`/`resume()`. They must not be
  swallowed by a position-update method, or pause counts and total pause time
  will be lost.
- If Spotify broadcasts or Android MediaSession omit pause/stop events, Tempo
  has limited ways to infer when the audio stopped. OEM background restrictions,
  notification permission and Spotify app behavior can still affect accuracy.
- An Android app does not receive an authoritative stream of audio samples
  solely through MediaSession. Elapsed playing time is an **observational**
  measure based on reported state, not proof that audio was audible.

## Device acceptance test (perform after building)

1. Play Spotify and verify Now Playing appears promptly.
2. Play continuously for 35–60 seconds. Confirm the live-session timer
   increments approximately once per second.
3. Pause 10 seconds. The live session timer should freeze; resume should add
   time without including the paused interval.
4. Skip to the next song after the previous play passes Tempo's configured
   minimum. Its history entry and Home/Stats totals should refresh after the
   write completes, without the previous 30-second queue delay.
5. Seek forward one minute. Live listening minutes must **not** jump by a minute.
6. Try 2x or 0.5x playback. Wall-clock listening time should count seconds,
   while track progress/completion uses media position.
7. Use a notification-only music player with no usable MediaSession position.
   A sufficiently long play should still be recorded.
8. Run automatic Spotify API reconciliation later and check the same local
   play is not counted twice. Spotify may report it with an end timestamp.
9. Disable tracking; stale local Now Playing should clear.

No Android APK workflow is required to review these commits. Builds and
on-device verification must be performed explicitly by the app owner.

# Tempo follow-up code fixes — 2026-11-17

Target branch: `fix/background-tracking-reliability-2026-11-16`. This work deliberately stays off `main` because pushing to `main` triggers APK workflows. **No APK, extension bundle or release was built/published; unit tests are authored but not executed.**

## Reliability fixes

- Complete plays are staged to a durable event queue before the 750 ms Room batch window; successful writes remove their staged copies, while unexpected batch exceptions retain them for offline retry.
- Offline events no longer expire after five failed attempts or evict oldest plays at 1,000 entries. Backoff stays capped at 60 seconds. Offline staging and session checkpoint SharedPreferences writes use synchronous commits; write failures are visible.
- Offline queue identity and content fingerprints include observed session ID; the in-memory filter and first database-dedup layer no longer collapse distinct sessions with equal timestamps. Import fingerprints remain backward compatible.
- Session persistence begins immediately and continues through pauses. Ended sessions clear old recovery state, checkpoint writes are serialized, and failed shutdown snapshots block the recovery-record cleanup.
- Failed MediaSession initialization is released and retried; the active playing controller is preferred where an app exposes multiple sessions. Listener reconnection avoids the redundant immediate scan.
- Unknown position coverage no longer automatically marks a play skipped. Playback position tracking accepts advancement at the reported playback speed while actual listening minutes remain wall-clock time.
- Desktop ingestion defers acknowledgments if any record failed. The server returns retryable HTTP 500 for `partial_failure`, preserving clients' queued batch for idempotent retry.
- Desktop replays are reconciled transactionally with a one-second timestamp-jitter tolerance, not suppressed for five minutes; desktop import invalidates cached stats.
- Browser extension refuses unparseable or undecryptable HTTP-200 sync acknowledgments, retaining queued plays.
- Spotify metadata changes cannot inherit playing=true from an older track.

## Accuracy semantics

Desktop `duration_ms` is catalog duration, not observed listening time. A legacy client without `listened_ms` still contributes one play, with zero *verified* listened minutes and no fabricated completion or skip. A transmitted `listened_ms=0` remains zero and is excluded by the five-second minimum, not replaced with the full song duration.

## Follow-up testing required before release

1. Compile Kotlin and run JVM regression tests (new replay, playback-speed, listener-heartbeat, desktop-duration and session-key tests).
2. On Android 13–16: begin playback, background the app, lock the screen, turn off screen and test doze; kill/rebind listener, pause/unpause and restart Spotify; verify each completed session recorded once with wall-clock minutes.
3. Simulate Room failure for longer than five retry attempts, reboot while offline and verify all recovered events persist exactly once. Test low-storage and interrupted SharedPreferences commits.
4. Desktop: send two back-to-back plays of one track, then replay a partially failing batch; confirm HTTP 500, automatic retry, precise play count and no invented duration. Reject corrupted HTTP-200 acknowledgments.
5. Exercise multiple MediaSession tokens, changed Spotify metadata arriving before playback broadcast, and 0.5x / 2x playback speed.
6. Regenerate browser extension `dist` during an explicitly authorized build; only extension source is updated here.

## Open risks and deliberate boundaries

- **Desktop security:** The current local HTTP protocol still transmits a bearer token in the Authorization header. HMAC and encrypted payloads do not conceal that header from network observers. Fix requires a coordinated challenge-response / TLS-compatible migration of Android, existing desktop clients and browser extension; changing the server alone would break pairing.
- Android OEM battery restrictions and Android foreground-service mediaPlayback policy require real-device/product validation.
- One-session-per-app is still the model: selecting the playing token improves accuracy but does not record two simultaneous tokens from one package.
- Long outages can increase the durable SharedPreferences queue indefinitely; a future Room-backed outbox is advisable.
- Device tests, compilation and APK publishing have NOT been performed.

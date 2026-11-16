# Background tracking reliability audit — 16 November 2026

**Scope:** Source-only audit of the current `main` branch at `11000a1327aad6fa4e7270752df6b7620d346a4f`, focusing on notification-listener lifecycle, Android media sessions, session preservation, Spotify device broadcasts and API reconciliation, boot recovery, and health monitoring.

**Important:** This repair branch is intentionally unbuilt and untested on a device. Do not merge, assemble an APK, publish a release, or trigger CI without the owner's explicit approval. The main branch's push workflow assembles a debug APK, and a PR would trigger the CI assembly job; leave this branch as a standalone commit for review.

## Confirmed defects and repairs

1. **Listener disconnected while the foreground process remained alive.** The persisted heartbeat used the process's alive timestamp to clear failed-rebind escalation, even if the notification listener was disconnected. A new process could also inherit the previous process's `listenerConnected=true` flag. Reset connection state on service creation, evaluate the current process's listener connection separately, and escalate a failed rebind using a new `onListenerConnected` timestamp rather than the process heartbeat.
2. **Slow/suppressed health recovery.** Health work ran with a 30-minute period plus `batteryNotLow` constraint; Android can defer it longer. Reduce the requested period to 15 minutes without the battery constraint. This is still best-effort, not a guaranteed real-time watcher.
3. **Boot without listener reconnection.** The boot receiver previously re-enabled the listener only when the component was disabled. It now also requests a notification-listener rebind for an enabled component and checks that the exact component is granted notification access, without trying to start a restricted media-playback foreground service from boot.
4. **Media-session observer permanently unavailable after early initialization failure.** If `getActiveSessions`/listener setup fails while the system listener is not yet connected, subsequent scans previously exited without retrying. Retry setup from the listener's connected callback and later background heartbeats, and cleanly unregister the manager observer at disconnection/destruction.
5. **Stale media controller after a source application's token replacement.** The old controller map only compared package names, so Spotify reconnects and player restarts could strand a callback on a dead token. Compare session tokens, unregister the old callback and attach the replacement. Ignore destruction callbacks from an old token after replacement.
6. **Misleading notification removal.** Any removed notification from a package with an active playback session could end the listen, even an unrelated alert. Track actual posted media notification keys; don't finalize while another media notification or a live playing controller still exists. Rebuild the keys on listener reconnection.
7. **Spurious listening after notification refresh.** A routine notification update used to resume a paused session unconditionally, including after returning from background. Use a valid MediaSession play/pause state when available; retain notification-only fallback where no controller exists.
8. **Unobserved playback gap.** When the notification listener disconnects, pause the in-memory monotonic playback clocks and persist the sessions so a disconnected interval is not blindly counted as playback.

## Other audit findings and limitations

- **Android background restrictions:** A notification-listener binding and a foreground notification are distinct. OEM battery limits, notification access revocation, force-stop, and Android's foreground-service rules may still prevent immediate recovery. No application can guarantee callbacks during a force-stop or revoked permission.
- **Spotify verification:** The `SpotifyPollingWorker` reconciles recent tracks on a requested 20-minute WorkManager cadence when the Spotify history-sync preference is enabled. It is not a real-time playback feed and can be deferred by Doze or networking constraints. Keep MediaSession/device-broadcast events primary and the API supplementary.
- **Snapshot limitation:** One active MediaController per source package is tracked. Players exposing multiple simultaneous media-session tokens need a dedicated policy beyond this narrowly scoped reliability repair.
- **Event-loss risk:** Playback session autosave runs about every 30 seconds; a hard kill before the next snapshot can lose that unsaved interval. Batch/offline recovery and persisted session IDs reduce but do not eliminate this gap.
- **Foreground-service compliance:** Tempo declares `mediaPlayback` foreground service type for a listener that primarily observes third-party playback. Reassess the Android/Google Play policy fit before any store release. Changing the service type without device and policy validation is outside this repair.

## Validation checklist for a future explicitly authorized test/build

- Run focused JVM tests, especially `TrackingServiceHeartbeatTest` and existing playback-session/reconciliation tests; then run broader tests and lint.
- On device: play Spotify and another supported player, lock the screen for 30+ minutes, pause/resume and skip repeatedly, and confirm the saved duration and history update after reopening Tempo.
- Restart Spotify and switch between local/Connect playback to verify controller-token replacement and no duplicate events.
- Turn notification-listener access off and on, reboot, and test both battery-saver and battery-not-low states. Confirm listener recovery and no inflated time over disconnected gaps.
- Confirm non-media alerts from the music app do not end active plays; confirm a genuine media notification removal with no active controller does.
- Test force-stop explicitly; Android generally prevents background restart until the user opens the app, so document the expected limitation instead of promising recovery.
- Inspect real `adb logcat` exceptions, `WorkManager` state and battery optimization behavior across Android 13–16 and OEM skins. The repository-only audit cannot substitute for these device checks.

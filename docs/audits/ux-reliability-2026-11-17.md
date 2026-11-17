# Tempo — UX and query reliability implementation pass

**Date:** 2026-11-17  
**Baseline:** `8ab6fe585a1690c6cbd8ed7811d68a0171f66412`  
**Status:** Implemented and committed to `main`. **No builds, tests, automated CI runs, APKs or releases triggered.** Compilation and device behavior remain unverified for this change set.

## Completed

### Reliability
- `ui/stats/StatsViewModel.kt`: generation-gated results; tracked paging jobs; obsolete search, sort, tab, refresh and pagination results ignored; transient loading flags cleared on cancellation; analytics cache marked only after a current result commits.
- `ui/history/HistoryViewModel.kt`: generation-aware primary/Last.fm/archive jobs; filter or search invalidates old requests; refresh and pagination results guarded; cancellation propagated; transient indicators reset.
- `ui/stats/StatsScreen.kt` and `ui/history/HistoryScreen.kt`: `derivedStateOf` and effects bound to the **current** `LazyListState` after filters recreate the list.
- `ui/components/NowPlayingArtworkViewModel.kt`: require exact title/artist matches, reject conflicting known albums, keep player/Spotify URI fallback. Regression assertions added in `NowPlayingArtworkChoiceTest`.

### UI and performance
- `ui/home/HomeScreen.kt`: hide empty Now Playing card, default to a focused listening dashboard, expand Spotlight/top-track/insights on demand. Secondary dialogs no longer overlap consent or user-requested overlays.
- `ui/home/HomeViewModel.kt`: defer secondary rankings, Spotlight art, insights aggregation and gamification detail queries until user expands discovery; core today/summary remain eagerly available.
- `ui/navigation/TempoBottomNavigation.kt`: add text labels and reduce exaggerated active-tab scale.
- `ui/settings/SettingsScreen.kt`: category overview for General, Music Tracking, Connections & Imports, Library & Data, About & Community; preserve existing controls and behavior inside groups; Android Back returns to category list; show notification-access state with a direct permission shortcut; poll battery only in relevant groups.
- `ui/stats/StatsScreen.kt` and `ui/home/HomeScreen.kt`: move date-range selector into scrolling content to avoid overlaying bottom navigation; Stats keeps ranking tabs sticky but not the time selector.
- `values/strings.xml` and supported locales `de`, `fr`, `hu`, `pt`, `ru`: new copy localized.

## Outstanding / intentionally deferred

1. **Secure desktop protocol migration:** extension currently connects to `http://` and carries a bearer token in a header; signed/encrypted bodies do not protect this credential from passive LAN capture. Design versioned key-based request authentication (without transmitting reusable bearer secrets) and a coordinated extension/app migration; preserve existing installations until they upgrade.
2. **Undo history deletion:** current deletion removes listening events and potentially orphaned tracks. A reliable Undo requires a transactional restore record or durable deferred-delete protocol; avoid a UI-only timer that silently drops deletes if the ViewModel is destroyed.
3. **Tracking health diagnostics:** expand the new permission status into last observed event, active source, freshness, foreground-service/lifecycle status, device battery restrictions, and safe logs export. Do not present permission access as proof that tracking is active.
4. **Backup status and easy restore verification:** surface latest local/Drive successes and errors; check old backups are restorable and Google Cloud OAuth is configured on the user's signing certificate.
5. **History metadata correction shortcut, Home card customization, accessibility/reduced-motion defaults:** separate follow-up UX increments, using resource strings and a consistent state/persistence approach.
6. **Performance measurement:** macrobenchmark cold launch/scroll, Compose recompositions, Room queries and CPU while playing, memory, battery and artwork cache hit rate. Do not claim quantitative wins without a real device or emulator.
7. **User acceptance on debug APK:** validate scroll/overlay behavior, permission settings, artwork mismatch protection, and races before promoting the next public test build.

## Required validation when explicitly authorized to build

- Fast switch between songs/artists/albums, sort criteria and time ranges while Stats loads more; no stale pages or persistent spinners.
- Rapid History search, view-mode and date-filter switches while fetching recent, Last.fm and archive items; no duplicate keys, skips or lost items.
- Home cold open should show today/summary before detailed insight requests; expansion should load top artists, tracks and Spotlight correctly.
- Test media artwork from Spotify broadcasts, MediaSession `content://` URLs, local music and Spotify Connect; do not show another album's artwork when known release identity conflicts.
- Check five Settings categories, OS Back, full data import/export, signed-in status, backup entry and permission return flow.
- Test translations, TalkBack, text scaling, 320dp layout, landscape, keyboard behavior and navigation across all pages.
- Confirm historical listening minutes, repeat plays, backup compatibility and background tracking unchanged.

**Build policy:** per `docs/BUILD_AND_RELEASE_POLICY.md`, commits to `main` must not trigger builds, CI or releases. User will explicitly authorize a build and, separately, a release.

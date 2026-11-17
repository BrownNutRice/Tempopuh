# Appearance foundation — implementation checkpoint

**Date:** 2026-11-17
**Branch:** `main`
**Rebrand:** Not started
**Build and release policy:** CI and APK builds remain manual-only; no release created.

## Implemented

- `ui/appearance/AppearanceSettings.kt` defines stable preset, tone and typography identifiers, safe unknown-ID fallbacks, and reduced-motion preference.
- `AppearancePreferencesRepository.kt` stores style/font/motion in a dedicated `tempo_appearance` DataStore; no Room schema change or tracking-source dependencies.
- `AppearancePalette.kt` defines four dark-compatible palettes: **Abyss**, **Obsidian Gold**, **Aurora**, **Glacier Night**. Preset roles include background, foreground, accent, card surface, border and navigation.
- `TempoTheme` listens to preferences through the existing Hilt `MainActivity` root, preserves the original legacy Abyss Material3 scheme as initial default, and provides `LocalTempoPalette`/appearance settings down the Compose tree.
- `DeepOceanBackground`, `GlassCard`, `TempoBottomNavigation`, `SettingsSwitch`, `TimePeriodSelector` and major accent elements in Home/Stats/History/Settings now read active semantic palette accents.
- `AppearanceScreen` under **Settings -> General -> Appearance** presents a two-column miniature player/stats preview gallery. Selecting a palette is a **draft**, and Apply persists the choice; Back/Cancel discards the draft. The selected saved preset has a visible check and accessibility semantics.
- Typography choices are **Tempo**, **Neo Sans**, **Archive Serif**, **Studio Mono**. They change heading roles using Tempo's bundled font or Android system families; body fonts and platform text scaling remain intact. This selection applies immediately.
- Reduce Animation preference OR system animation disablement flows to existing `rememberReducedMotion()` consumers; OS setting refreshes on Activity resume. Animated press/navigation scale responds to the preference.
- New appearance copy is available in default English plus German, French, Hungarian, Portuguese and Russian.
- Unit tests added for preset/color identifiers, safe unknown-font fallback and distinct palettes; **not executed**.

## Intentionally incomplete

1. **True light and system tone modes:** Not selectable yet. Many legacy screens still use dark-only constants, and the initial Glacier look is a *dark, icy-blue variant*, not the bright Glacier proposal. Implement true light surfaces, text/chart states, image overlays and system-bar contrast before enabling them.
2. **Full semantic token migration:** Some screens and illustrations still contain hardcoded teal, gold or black. This first pass migrates high-impact surfaces and dominant accent controls, not every single element.
3. **Dynamic wallpaper, album-art accents, adaptive GPU effects, progressive blur, glass refraction, appearance-specific shape/density controls:** Planned but unimplemented. Do not claim they work.
4. **Font selection preview:** Font choices apply instantly; palette choices require Apply. The UI labels distinguish this, but user tests should confirm it is clear.
5. **Real device performance:** No visual snapshot, Android compile, unit-test execution, JankStats, power, thermal or low-RAM measurement performed.

## Required validation before authorized build or release

- Assemble APK + run unit tests, lint and instrumentation only when user explicitly requests the build/CI step.
- Verify six supported locales and first-time preference hydration. Test unknown and corrupt stored preset/font enum strings.
- Verify no theme flash on startup; background service remains unaffected while changing palettes.
- Check gallery layout at 320dp, high contrast, TalkBack selection, landscape and large font scales.
- Check each applied preset on Home, Stats, History, Settings, Profile, Song Details, dialogs, album-art tiles and bottom nav. File inconsistencies rather than enabling system light prematurely.
- Check reduce motion toggles while a screen is active and OS animator scale changes on resume.
- Verify preserved listening events and no cloud requests triggered by appearance previews.
- Measure scrolling FPS, frame timing, app process memory and thermal pressure for all palettes.

## Next prioritized engineering increments

- **A2:** Finish semantic token migration through Profile, Track/Album/Artist detail screens, chart palettes and share canvas.
- **B2:** True contrast-tested Glacier Light plus System/Dark/Light and dynamic system-bar icons; preserve Abyss default.
- **C1:** Optional artwork accent on Now Playing; performance-aware flat/frosted surface switch.
- **C2:** Card density, corner style and improved visual accessibility.
- **D:** Optional advanced glass and progressive blur only after device tests.

Read `docs/APPEARANCE_PERSONALIZATION_PLAN.md` for the full roadmap.

## Continuation checkpoint — A2 screen migration (2026-11-17)

- Profile text, neutral surfaces, separators, action accents and trophy filters now consume the active palette. Level tiers, badge art/rarity, challenge difficulty and status colors retain their independent meaning.
- Song, artist and album detail text, controls, header scrims, fallback artwork surfaces and timeline ink now consume active palette roles. Existing artwork accents remain artwork-derived.
- A shared `rememberArtworkAccent` resets immediately on artwork/preset changes, uses the selected accent while loading or on failure, tries song backup artwork, and computes palettes off the main thread. Structured cancellation replaces asynchronous palette callbacks so an old image cannot overwrite a newer item's state. Album display no longer requests a duplicate palette callback.
- Analytics overview gradients and labels use active surfaces/text; hourly chart bars retain the active Material primary.
- Stats-share configuration controls and share-canvas toolbar/dock follow appearance. Exported share-theme palettes, canvas backgrounds, user text and artwork remain explicit design choices.

Validation performed: reviewed the source diff, checked balanced delimiters in changed Kotlin files, verified manual-only CI/APK triggers and ran `git diff --check`. No Android compile, unit tests, lint, emulator, APK build, workflow dispatch or release was run. Visual contrast, navigation while artwork loads, offline backup fallback, TalkBack and large-font behavior still require the authorized validation step.

A2 is **not complete across all shared UI**: generic dialogs, other analytics visualizations, reusable artwork overlays and picker sheets still need an audit/migration. Continue there before enabling true light/system modes. Other pending increments listed above remain deferred; rebranding has not started.

## Continuation checkpoint — draft and shared controls

- Palette, font, reduced motion and Restore Default are now one local draft. Apply writes the complete supported configuration atomically; Cancel/back leaves saved preferences unchanged. Editing waits for initial preference hydration. Changes are disabled during save, failures retain the draft and show localized retry guidance.
- Schema v1 is written on Apply; existing unversioned preferences remain readable. Future schema versions are protected from writes by older code. Corrupt appearance storage resets only appearance preferences; transient read I/O failures emit safe defaults and retry. This does not implement appearance backup/export.
- Shared Tempo dialog surfaces, text, buttons and input fields now use appearance roles. Share card/background picker sheet chrome follows the palette, while background design thumbnails keep their intended colors.
- Updated font instructions and save-failure copy in all six supported locales.

Validation: source review, XML parsing and `git diff --check` only. No compilation, tests, CI, build or release executed. Device behavior and storage-failure paths remain unverified. Remaining A2 audit: other chart families, reusable artwork overlays, bespoke dialogs and inline text editor controls. Next: finish these consumers before exposing true light/system modes. Whole-screen draft preview, backup/export and advanced effects remain future work.

## Continuation checkpoint — bespoke editing dialogs

Artist rename/split and song/album/artist merge dialogs now use selected appearance roles for neutral surfaces, text, borders, focus, selection and loading indicators. Analytics disclosure accents follow the selected palette. Error/status colors are preserved. The interactive trend line was audited and already uses Material primary for its line/fill; no change was needed.

Validation: source diff review and `git diff --check` only; no builds, tests, workflows or release. Remaining audit includes reusable artwork overlays, other chart families and inline text editor controls. True light/system support remains deferred.

## Continuation checkpoint — artwork, Home and editor controls

- Artwork atmosphere scrims now use the selected background; failed loads clear the previous artwork. Reduced-motion changes restart the effect with the current animation policy.
- Home summary/listening controls and insight-card neutral text, borders, surfaces and decorative primary accents now use appearance roles. Category/data colors and fixed contrasting editorial pills remain independent.
- Inline text editor chrome, font selection, toolbar and color-selection accents follow appearance. User-authored text colors, outlines, background colors and color-wheel choices are preserved.
- Generic StatCard entrance scale/fade now respects effective reduced motion.

Validation: source review, balanced-delimiter checks and `git diff --check`. No compilation, tests, CI, build or release. Concurrent reference-image changes on main were preserved.

The migration is still not a guarantee of whole-app light compatibility: remaining bespoke surfaces and fixed white/black overlays require contrast review across the app. Do not expose light/system modes until that audit and authorized Android validation are complete. The next larger milestone remains true Glacier Light and system-bar contrast, followed by Ember/Solstice and layout controls.

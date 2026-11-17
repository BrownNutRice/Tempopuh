# Manual build and release policy

**Status:** Active since 2026-11-17, at user request.

## Default for all development commits

- Code fixes, new features, documentation changes, PRs and pushes to `main` must **not** automatically start CI jobs, unit tests, Android emulator jobs, lint or APK builds.
- `.github/workflows/ci.yml` runs **only** through `workflow_dispatch`. It includes unit tests, APK compilation, lint and Android emulator tests when explicitly started.
- `.github/workflows/build-apk.yml` runs **only** through `workflow_dispatch`. It runs unit tests, assembles a debug APK, and uploads a temporary artifact for testing.
- The previous first-fork-preview publishing job was removed from `build-apk.yml`. No GitHub Action in this repository automatically creates a release or publishes an APK.
- `.github/workflows/play-crash-rate.yml` remains scheduled: it monitors Android vitals and may open an issue. **It does not compile, package, upload or publish the app.**

## Release authorization

Do **not** run `workflow_dispatch` for build or CI, create releases, push tags for publishing, or upload APK/AAB packages unless the user explicitly authorizes the relevant step in the conversation.

An authorization to commit, push, merge, resolve issues or make performance upgrades **is not** authorization to build or publish. Authorization to build is **not** authorization to publish a release.

When the user later asks to build, dispatch the selected workflow on the requested commit/branch, review the result, and share the artifact. When the user separately asks to publish, confirm the desired version, changelog, signing, compatibility and distribution target, and perform the release as a distinct action.

## Re-enable automated CI, only by explicit request

Restore `push` and/or `pull_request` workflow triggers if the user specifically asks for continuous integration again. These manual-only settings are deliberately in version control; future merges should preserve them.

## Notes

This policy applies to workflows owned by this GitHub repository; it does not alter external hosting settings or app-store dashboards. Manual compilation on a developer's own machine is outside GitHub Actions.

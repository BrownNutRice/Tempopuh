# Tempo release and versioning policy

## Version format

Tempo uses Semantic Versioning for display names and Git tags:

- Betas: `MAJOR.MINOR.PATCH-beta.N`, tagged `vMAJOR.MINOR.PATCH-beta.N`.
- Stable releases: `MAJOR.MINOR.PATCH`, tagged `vMAJOR.MINOR.PATCH`.
- Release titles use `Tempo MAJOR.MINOR.PATCH Beta N` for prereleases and `Tempo MAJOR.MINOR.PATCH` for stable releases.

The first beta on this series is `4.9.0-beta.1`. The old `4.8.16-fork.*` tags remain unchanged as historical releases.

Android `versionCode` is a separate monotonically increasing integer. Increase it for every installable APK, including beta iterations. It must never decrease, even when a display version is corrected.

## Beta publishing

- Build and validation workflows are manually dispatched from `main` only; commits and pushes do not build or publish.
- A beta workflow runs unit tests, Android lint, debug APK assembly, emulator tests, extension typecheck, and extension smoke checks.
- The release job runs only when the dispatch explicitly selects `publish_beta=true`, and only after every validation job passes.
- Publish a new immutable tag and GitHub prerelease with the APK and SHA-256 checksum. Never replace an existing release tag or asset.
- Keep real-device QA status in the beta notes. Automated emulator tests do not complete physical appearance, accessibility, background tracking, or battery validation.

## Stable publishing

Stable releases require a separate explicit request after beta issues and physical QA are reviewed. Do not infer stable release approval from a successful beta workflow.

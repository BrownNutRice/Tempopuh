# Release procedure

## Version and channel

Use `MAJOR.MINOR.PATCH-beta.N` for beta display versions and `vMAJOR.MINOR.PATCH-beta.N` for tags. Stable versions omit the suffix. Titles follow `Tempo X.Y.Z Beta N — Description` or `Tempo X.Y.Z — Description`. Legacy `4.8.16-fork.*` tags retain their original identities.

Android versionCode increases for each new installable build. Do not change package ID, OAuth callbacks, signing identity or stored-data formats as a release-label cleanup.

## Prepare

1. Resolve the intended scope against `main`; distinguish fixes from open proposals.
2. Update the display version/versionCode, `Changelog.md`, the versioned note under `docs/releases/`, and workflow version/artifact expectations together.
3. Document source commit, minimum Android version, APK type, signing continuity, upgrade steps, checks actually performed and remaining device limitations.
4. Confirm the stable signing key is available and compare the APK certificate with the intended public fingerprint. Signature verification alone does not establish continuity; [#49](https://github.com/matchalattewithbanana/Tempo/issues/49) tracks the missing automated gate.

## Validate and publish

Build/CI and publication require the relevant explicit request. Ordinary main commits run neither. Manually dispatch validation from main, inspect unit/lint/platform/extension results and keep test artifacts. Device visual, accessibility, tracking and battery checks are separate from green automation.

Only publish after the requested checks succeed and publication is authorized. The beta workflow's `publish_beta` input controls publication; a validation-only request keeps it false. Stable/store publication is a separate decision.

Create a new tag/release for changed binaries. Attach the APK and SHA-256 checksum, set prerelease status correctly and identify the matching source commit. Never move historical tags or replace published APK/checksum assets during a metadata cleanup.

## Catalog and corrections

Maintain [the release catalog](releases/README.md). Correct misleading titles, links or notes without claiming the attached binary changed. Record the date of substantive metadata corrections. Older previews stay visible as historical prereleases, not competing current recommendations.

Published artifacts reflect their tagged source. An unreleased documentation/copy fix is not part of an older APK. No store listing, Play Console declaration, provider OAuth setting or upstream website is updated by changing repository documents.

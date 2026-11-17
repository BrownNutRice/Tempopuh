# Contributing to Tempo

This guide applies to [matchalattewithbanana/Tempo](https://github.com/matchalattewithbanana/Tempo), a fork of the original Tempo project.

## Start here

Read the [development guide](docs/DEVELOPMENT.md), [architecture map](docs/ARCHITECTURE.md), [privacy policy](PRIVACY.md) and complete [LICENSE](LICENSE). Keep changes focused and distinguish implemented behavior from proposals.

The maintained development line is `main`. Use the fork's issues and pull requests; do not assume upstream maintainers operate this fork. Historical experiments are not active release branches.

## Changes and validation

- Describe the problem, resulting behavior, relevant issue and validation performed.
- Keep listening history, package identity, signing compatibility and backup/import formats stable unless a reviewed migration requires a change.
- Put network/persistence work in existing data/repository boundaries; keep screens focused on state and interaction. See the architecture map before introducing another global scope or service.
- For UI changes, support Light/Dark/System/OLED, large text, compact windows, RTL, accessible names/state and reduced motion. Actual screenshots/device evidence are required for visual acceptance.
- For tracking/import changes, test pause/seek/replay, duplicate sources, cancellation and restart without discarding unsaved plays.
- For performance changes, preserve correctness and compare the same device/fixture/build type. Source inspection is not a measured speedup.
- Run checks appropriate to the change and report unavailable checks honestly. Documentation-only work needs links/schema consistency, not an APK build.

GitHub CI/build/release workflows are manually invoked. Permission to commit or push is not permission to dispatch a build or publish. See [build and release policy](docs/BUILD_AND_RELEASE_POLICY.md).

## Privacy and security

Metadata queries, linked accounts, exports, backups and paired-device sync can transmit personal listening information. Do not add claims that all data stays local or that ordinary SQLite/ZIP is encrypted.

Keep credentials and personal exports out of commits, issues and screenshots. Automatic health reporting uses a closed schema and build/notice/preference gates; do not add free-text payloads or enable reporting in repository configuration. Update [analytics](docs/ANALYTICS.md) and privacy declarations when behavior changes.

Use [SECURITY.md](SECURITY.md) for sensitive reports and [SUPPORT.md](SUPPORT.md) for ordinary bugs. Do not change license terms, attribution or branding as incidental cleanup.

## Pull request format

State the user-visible problem and final behavior, list relevant checks/results, and identify material limitations. Reference issue numbers. Include UI captures when appearance changes. Do not mark a roadmap task complete solely because code compiles.

# Backup and restore fixes — 18 November 2026

Scope: issues [#40](https://github.com/matchalattewithbanana/Tempo/issues/40) and [#41](https://github.com/matchalattewithbanana/Tempo/issues/41), based on main `c605dfb`. These changes have not been packaged or released.

## Automatic backup retention

Successful verification records a per-document timestamp in app-private preferences. Retries retain that timestamp. The document URI, rather than its display name, identifies recorded creation order. The newly verified URI is always protected during cleanup, including reuse of a previous successful copy.

Legacy fallback is a positive provider modification date, then a strictly parsed timestamp filename in the current local timezone. UUID filenames are never interpreted as dates. Undated documents are preserved; a folder may exceed five files rather than risk deleting an unorderable backup. Provider-renamed documents remain eligible when their URI has recorded metadata. Enumeration/metadata/deletion failures do not turn a verified backup into a failed operation.

## Artwork restore lifecycle

Image entries are extracted into a unique cache directory. Parse, decompressed-size, duplicate-image and version failures reject the operation before promotion. Validated artwork is renamed into a unique operation directory beneath `files/album_art/`; archive filenames can never truncate an existing image.

The DB transaction uses those promoted paths. Transaction-body failure removes only this operation's files. The last transaction statement retains artwork before commit can make references visible. If commit outcome is uncertain during cancellation/return, files are retained conservatively: an unreferenced directory is preferable to deleting artwork a committed row may reference. Post-commit profile/cache failures also leave committed artwork intact. Cleanup removes private staged image bytes on normal rejection/cancellation. A hard process kill can leave an orphan operation directory; automatic orphan reclamation is not part of this patch.

## Validation

Eight JUnit 4.13.2 regression tests passed with the repository's Kotlin 2.2.10 compiler, compiled independently of Android/Gradle:

- UUID order versus creation order and protection of the newest destination.
- Retry protection of an older verified URI.
- Provider-renamed identities, absent/zero dates and mixed dated entries.
- Strict legacy timestamps and rejection of UUID/invalid dates.
- Rejected-archive collision preservation and staged-byte cleanup.
- Transaction-body failure cleanup without modifying live files.
- Repeated restores retain distinct committed image paths.
- Oversized and duplicate image entry rejection.

`git diff --check` passed. The Gradle wrapper could not download Gradle 9.4.1 (`Network is unreachable`); the Android application and instrumentation suite were not compiled or run. No APK build, CI dispatch or publication was requested. SAF provider persistence/restart behavior and end-to-end Room restore cancellation remain device/emulator validation items.

## Next data-safety work

[#42](https://github.com/matchalattewithbanana/Tempo/issues/42) remains open: exporting with ID ceilings alone does not provide a consistent database snapshot during concurrent merges/edits. Account disconnect and full-reset races remain tracked separately in #43 and #44. These fixes do not claim to resolve those findings.

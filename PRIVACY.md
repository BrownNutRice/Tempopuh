# Tempo privacy policy

**Updated:** 18 November 2026
**Scope:** the Android app built from this fork. Build configuration and enabled integrations affect which data flows are active. The browser companion has a [separate policy](browser-extension/PRIVACY.md).

## Overview

Tempo stores its primary listening library locally. It has no Tempo-hosted listening-account service in this source tree. It can contact music/metadata providers, use linked Spotify or Google accounts, receive paired-device plays, and create backups or shared files. These features mean that some data can leave the device.

The source contains no advertising or attribution SDK. Optional app-health reporting is described below. This document describes implemented behavior; it is not a claim that every device, provider or security scenario has been certified.

## Data stored on the device

| Data | Purpose |
| --- | --- |
| Track, artist and album details; listening timestamps, durations and playback evidence | History and statistics |
| Imported history, import status, aliases and content corrections | History reconciliation and user edits |
| Preferences, profile name/photo, badges and challenges | Personalization and optional gamification |
| Artwork and metadata caches | Faster/offline presentation |
| Active-session recovery and pending events | Recover tracking after interruptions |
| Linked-account credentials and pairing information | Authorized provider calls and companion sync |

The library uses Room/SQLite in Android app-private storage. **The app does not configure an encrypted database.** Android's device/storage protections are separate from application-level encryption. Some credentials use Android Keystore-backed storage; that does not encrypt the listening database or every preference.

## Data sent to other destinations

| Feature | Destination and data |
| --- | --- |
| Metadata enrichment and artwork | Providers such as Spotify, MusicBrainz/Cover Art Archive, Apple/iTunes, Last.fm, Deezer and ReccoBeats receive relevant artist/title/album search terms, music identifiers, artwork requests or public preview URLs. Requests may run in background enrichment jobs. |
| Spotify connection | Spotify receives OAuth/token requests and authorized API calls; Tempo can retrieve account-related music data and recent history. Credentials remain available locally for those calls until disconnected. |
| Last.fm import | Last.fm receives the supplied username and API requests for its available listening history. |
| Google Drive backup | Google handles sign-in/authorization; an archive containing library data and optionally images is uploaded to the connected account's Tempo Backups folder using the drive.file scope. |
| Browser/desktop pairing | A configured companion and phone exchange playback records and pairing/control data. Use a trusted network. |
| Export, diagnostics and sharing | Files leave the app when you select an external destination or share target. That recipient controls its copy. |
| Configured health reporting | The configured analytics endpoint receives limited app-health events and a random installation identifier, as described below. |

Network recipients necessarily receive connection information, including the public IP used for internet requests. Provider processing and retention are controlled by those providers and the operator of a configured analytics deployment. This repository cannot guarantee their infrastructure location, logging or retention configuration.

Tempo's enrichment path does not upload local music/audio files. A public preview URL may be submitted for analysis. Search terms can disclose which music is being matched, so metadata requests are not described as containing no listening information.

## Backups and exports

Tempo backup archives are ZIP/JSON-based files with optional images. **They are not password-encrypted by the app**, including when uploaded to Drive. Choose destinations appropriate for personal listening records and protect exported files yourself. Transport or provider-managed storage encryption does not make an archive end-to-end or password encrypted.

A backup does not include every app-private store or credential. In particular, the current library archive does not serialize Appearance Studio's separate settings/saved-look store. Appearance has its own JSON transfer. Verify what you need before reinstalling.

Signing out or deleting local app data does not delete files already exported, shared, or retained in Google Drive. Manage those copies at their destinations.

## Permissions and access

| Access | Use |
| --- | --- |
| Notification access | Observe media notifications for tracking. This is a broad Android access grant; the implementation filters relevant media sources rather than receiving a music-only permission. |
| MediaSession access | Read available playback state/metadata to reconcile listens. |
| Foreground service and notifications | Maintain visible tracking/import/backup activity when Android permits it. |
| Internet and network/Wi-Fi state | Provider requests, optional reporting and paired-device discovery/sync. |
| Camera | Scan a companion pairing QR code when requested. |
| Boot and wake-lock capabilities | Support eligible background recovery/work. |
| Battery-optimization exemption | Optional help with background reliability; Android/OEM behavior still applies. |
| Selected document/folder/photo access | Read chosen imports/images and write chosen exports or automatic backup folders. |

The [Android manifest](app/src/main/AndroidManifest.xml) is the authoritative permission inventory for a particular source revision. Revoking relevant permissions can stop associated features.

## App-health reporting

Reporting is inactive when the build has no configured endpoint. Debug builds are also gated off unless a developer explicitly enables the preview override. A configured build currently defaults reporting to enabled **after the in-app notice has been rendered**; acknowledging the notice is not a separate opt-in requirement.

Events use a closed schema for startup failures, feature use, onboarding, detection health, aggregate listening-count ranges, imports, enrichment, backups, migration and crash signatures. The transport includes a random persistent installation identifier, event timestamps, app/OS version and locale; crash events additionally include device model. It excludes track/artist/album names, raw notifications, account credentials and file paths from automatic event properties.

Because an installation identifier is retained, reporting is **pseudonymous rather than guaranteed anonymous**. Payload flags request that PostHog disable person profiles and IP-based geolocation. Those flags do not prevent an endpoint from seeing network connection metadata or prove its operational retention practices.

Turn reporting off in **Settings → Your Data**. This stops collection under the app's gate, clears the unsent queue and resets the local installation identifier. Resetting the identifier affects future attribution; **it does not delete previously received server events**. The local queue is capped at 200 events with a 24-hour age limit. Server-side retention and deletion procedures must be supplied by the operator of a reporting-enabled distribution. See [analytics reference](docs/ANALYTICS.md).

## Controls, retention and diagnostics

Use account disconnect controls, tracking settings, permissions, queue controls and exports for their corresponding data. On current main, **Clear all data** requests Android’s full app-data reset. It closes Tempo and removes app-private data and its app-specific external storage, including the library, preferences, saved sign-ins, pairing information, local reporting queues and pending/recovery stores. Android’s operation also revokes runtime permissions and URI grants. Reopen Tempo to set it up again. A successful return only confirms that Android accepted the request.

Files outside Tempo’s app-specific storage, cloud backups, provider accounts, browser/desktop companion stores and previously received reporting events remain at their destinations. Clear or revoke those separately if desired. The published `4.9.0-beta.1` APK predates the [reset fix](https://github.com/matchalattewithbanana/Tempo/issues/44); its older partial-clear behavior has known gaps. Device validation of the new reset boundary remains pending.

There is no declared automatic one-year deletion policy for listening history. Metadata caches and temporary import/backup files have separate cleanup behavior. Keep a verified backup of records you want to retain.

Diagnostics are generated locally and can be reviewed before sharing. They include technical versions, aggregate library/metadata counts and tracking/work status. Do not attach raw databases, backup archives, tokens or unreviewed logs to public issues.

## Policy maintenance and contact

This policy is maintained with the source. Check the policy supplied with your installed version when comparing historical builds. Report documentation or privacy-behavior discrepancies in [this fork's issue tracker](https://github.com/matchalattewithbanana/Tempo/issues), omitting personal records and credentials. For sensitive security details, follow [SECURITY.md](SECURITY.md). Upstream contact details are not presented as this fork's privacy contact.

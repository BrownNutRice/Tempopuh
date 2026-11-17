# Tempo browser companion privacy policy

**Updated:** 18 November 2026
**Scope:** this repository's browser extension. See the [Android privacy policy](../PRIVACY.md) for the phone app.

## Information accessed and stored

The extension reads playback metadata and available player state on supported pages: title, artist, album, duration/position, pause/seek/replay evidence, volume/mute state and source page/site information. Ordinary YouTube tracking uses channel/artist rules. Browser tab access is used to identify relevant pages; this does not mean every page is recorded as a play.

IndexedDB stores play records and sync history. Browser local storage holds settings, artist/channel lists, pairing address/port and authorization token. Session storage holds transitional tracker/sync state and a token mirror. **These stores are not independently encrypted by the extension.** Protect the browser profile and exported files.

## Transmission

The extension syncs records directly to the configured phone endpoint and exchanges pairing/health/control messages. The intended setup is a trusted LAN, Wi-Fi or hotspot. Discovery probes known addresses and may probe the stored private subnet. The implementation supports HTTP/WebSocket endpoints; payload signatures and supported encrypted envelopes do not amount to a guarantee that all traffic uses TLS or remains inside a particular subnet.

Pair only with a trusted device and grant only needed host access. A manually configured address, network routing or proxy may change where traffic travels. Do not expose the phone service to the public internet or publish the pairing token.

The extension does not contain a central analytics or advertising integration. The music website, browser and chosen sync endpoint have their own processing. Data sent to Android is then subject to the Android app's storage, enrichment, backup and sharing behavior.

## Permissions

The manifest declares storage, alarms and tabs, supported-site content scripts, default HTTP host access on port 8765, and optional HTTP host access for pairing/recovery. The built Chrome/Firefox manifests may differ. See [manifest.json](manifest.json) and the build output used for your installation.

## Retention and controls

Tracking and offline mode are controlled in the popup. You can inspect/delete queued records, retry failures, export records or remove pairing. Unpairing removes the active pairing credentials; it does not erase existing phone records or all local playback/connection-history records.

Automatic age/cap cleanup targets **synced** plays. Queued and failed records are preserved rather than silently discarded at 5,000 rows. Sync-history cleanup has its own age/count limits. This can increase local storage while the phone is unavailable; review and export records before deleting them.

Exports contain listening information and are not password-encrypted. Removing browser extension data does not remove copies already synced or exported.

## Reporting concerns

Use the [fork issue tracker](https://github.com/matchalattewithbanana/Tempo/issues) for nonsensitive discrepancies. Do not include tokens, private URLs or raw listening exports in public reports. Use [security guidance](../SECURITY.md) for sensitive findings.

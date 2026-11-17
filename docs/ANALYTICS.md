# App-health reporting reference

**Reviewed:** 18 November 2026. This page describes the source; actual endpoint/operator configuration belongs to each distribution. Full context: [privacy policy](../PRIVACY.md).

## Activation and controls

`AnalyticsGate` in `data/analytics/AnalyticsConsent.kt` requires a configured build, an enabled preference and a rendered disclosure. The current default is enabled once that notice has been shown, not explicit opt-in after acknowledgement. Debug builds are gated off unless `ANALYTICS_DEBUG_PREVIEW` is deliberately enabled. An unconfigured source build sends no health events.

Settings → Your Data provides reporting and identifier controls. Opt-out clears the pending queue and changes future installation attribution. Resetting an ID does not erase events already received by a server.

## Payload

Reporting is pseudonymous. `PostHogPayload.kt` supplies a random installation `distinct_id`, event timestamp, app/OS version and locale. Crash events additionally include device model. It requests `$process_person_profile=false` and `$geoip_disable=true`. These flags do not hide the sender's network address from the receiving service.

The closed event schema excludes listening titles/names, raw notification text, credentials, file paths and per-listen timestamps from event properties. The event itself still has a transport timestamp. Counts/durations/sizes use defined buckets where specified; do not describe every field as anonymous or bucketed.

| Event family | Purpose |
| --- | --- |
| Startup and crash | Slow startup and constrained crash signatures |
| Feature/onboarding | Feature-first-use and setup outcomes |
| Tracking/listening activity | Health and aggregate count ranges |
| Import/enrichment/backup | Operation outcomes and bounded metrics |
| Database migration | Source/target schema versions |

For exact event names/properties, inspect `AnalyticsEvent.kt`, `AnalyticsCatalog.kt`, `AnalyticsSchema.kt`, `PostHogPayload.kt` and their tests. Crash signatures omit raw exception messages and full stack traces.

## Storage and delivery

The local queue retains at most 200 events for up to 24 hours. Delivery is batched and may use mobile data. Duplicate insertion IDs support retries. Network traffic size depends on activity and failures; no fixed daily bandwidth promise is made.

The endpoint/token are build-time settings in uncommitted developer configuration. The source does not establish a particular deployment's region, contractual relationship, server retention, access policy or deletion procedure. An operator enabling reporting must document those details before distribution.

## Diagnostics

The diagnostics screen produces a local, reviewable technical report. Sharing is a separate user action. Keep detailed troubleshooting in reviewable diagnostics rather than broadening automatic telemetry. Public issue reports should omit raw history, backups and credentials.

## Distribution declarations

See [privacy declaration checklist](PRIVACY_DECLARATIONS.md). A source-policy update is not a Play Console/App Store submission. Determine the correct declarations for the exact packaged build and its integrations; verify its actual network behavior and operator configuration.

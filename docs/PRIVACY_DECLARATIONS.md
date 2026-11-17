# Privacy and distribution declaration checklist

**Reviewed:** 18 November 2026. This is a technical evidence checklist for distribution maintainers, not a store submission, legal opinion or compliance certification.

## Verify the exact packaged build

| Area | Evidence to inspect | Declaration considerations |
| --- | --- | --- |
| Local library | Room configuration, manifest backup rules, export codec | App-private SQLite; no app-level DB or archive encryption configured |
| Music providers | Enrichment/import/auth endpoints and settings | Artist/title queries, identifiers, account requests and network metadata |
| Drive | Requested scopes, archive content and account controls | User-enabled off-device archive; external retention/deletion |
| Companion | Built manifests, pairing storage and transport implementation | Media/page data, host access, local-network intent and actual routes |
| Reporting | Build fields, AnalyticsGate, consent/defaults, payload and backend | Pseudonymous ID, event timestamps, versions/locale, crash device model |
| Diagnostics/sharing | Report/export preview and recipients | User-selected disclosure; exported files may contain personal records |
| Deletion | Queue/session/cache/account and external-copy behavior | Local clear/reset is not server or external-file deletion |

## Operator details needed for reporting-enabled distribution

Confirm the operator/contact, actual receiving endpoint, hosting region, recipient roles, access restrictions, retention, deletion process and provider terms. A source payload flag cannot prove a backend contract or log-retention policy. Resetting the local analytics identifier is not evidence of server erasure.

## Release checks

- [ ] Policy and in-app copy match the packaged behavior in every supported language.
- [ ] No claim that all data is local, all storage is encrypted or reporting is guaranteed anonymous.
- [ ] Notification access is described as a broad platform permission with app-side filtering.
- [ ] Optional integrations, default reporting behavior, destination data and user controls are disclosed.
- [ ] Any store data-safety/privacy answers are reviewed against the actual binary/network behavior and the operator's configuration.
- [ ] No statement implies this repository changed Play Console, an upstream website or provider settings.
- [ ] Historical release notes remain tied to their source/artifacts; current policy updates are not represented as retroactive binary changes.

References: [privacy policy](../PRIVACY.md), [analytics](ANALYTICS.md), [browser policy](../browser-extension/PRIVACY.md), [security](../SECURITY.md).

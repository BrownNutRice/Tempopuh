# Beta 1 reliability fixes

Baseline: main at `81a5cadb09d1b97daab6f97228cd475b7fcab6f7`. This batch addresses source findings; it does not declare the backlog resolved.

- #6: remove plaintext credential fallback and automatic encrypted-file/shared-key deletion. Reads tolerate unavailable secure storage; writes require committed encrypted storage. Failed writes prevent credentials from being exposed as a successful connection. Settings offers a localized retry message. Existing plaintext fallback is removed on secure-store initialization.
- #10: persist per-event retry deadlines (legacy records default due), process up to 100 due items per pass, and wake the retry worker on newly failed events. No serial per-event delay. Existing deduplication and persistence rules remain in place.
- #26: quarantine unreadable pending-event JSON before replacing or clearing active data. Keep a recovery flag and independently named payloads. Refuse overwrite if preservation fails, including commit(false) with changed in-memory preferences. Automated recovery UI/export remains to be designed.
- #19: only mark migration snapshots done after every existing file copy and synchronous marker write succeed. Failed partial directories are removed so future launches can retry. This remains a raw pre-Room copy, not a claim of a live SQLite snapshot.
- #20: replace analytics queue file atomically; propagate failed durable writes instead of silently succeeding. The public analytics tracker remains best effort. Feature-first-use ledger ordering remains a separate follow-up.
- #18: explicitly request access_token and the Play Developer Reporting scope from the existing Google auth action. Reference: https://github.com/google-github-actions/auth#inputs-generating-oauth-20-access-tokens . Credential permissions and Play API responses still require a configured workflow run.

Validation: changed Kotlin syntax, XML and YAML parse checks; git diff --check. Added Android recovery regression tests for preserved corrupt payloads and legacy empty queues; these have not run. No Gradle tasks, CI, emulator, APK build, release or tag was started. Issues stay open pending the appropriate runtime validation; current task still includes remaining backlog work.

# Beta 1 reliability fixes

Baseline: main at `81a5cadb09d1b97daab6f97228cd475b7fcab6f7`. This batch addresses source findings; it does not declare the backlog resolved.

- #6: remove plaintext credential fallback and automatic encrypted-file/shared-key deletion. Reads tolerate unavailable secure storage; writes require committed encrypted storage. Failed writes prevent credentials from being exposed as a successful connection. Settings offers a localized retry message. Existing plaintext fallback is removed on secure-store initialization.
- #10: persist per-event retry deadlines (legacy records default due), process up to 100 due items per pass, and wake the retry worker on newly failed events. No serial per-event delay. Existing deduplication and persistence rules remain in place.
- #26: quarantine unreadable pending-event JSON before replacing or clearing active data. Keep a recovery flag and independently named payloads. Refuse overwrite if preservation fails, including commit(false) with changed in-memory preferences. Automated recovery UI/export remains to be designed.
- #19: only mark migration snapshots done after every existing file copy and synchronous marker write succeed. Failed partial directories are removed so future launches can retry. This remains a raw pre-Room copy, not a claim of a live SQLite snapshot.
- #20: replace analytics queue file atomically; propagate failed durable writes instead of silently succeeding. The public analytics tracker remains best effort. Feature-first-use ledger ordering remains a separate follow-up.
- #18: explicitly request access_token and the Play Developer Reporting scope from the existing Google auth action. Reference: https://github.com/google-github-actions/auth#inputs-generating-oauth-20-access-tokens . Credential permissions and Play API responses still require a configured workflow run.

Validation: changed Kotlin syntax, XML and YAML parse checks; git diff --check. Added Android recovery regression tests for preserved corrupt payloads and legacy empty queues; these have not run. No Gradle tasks, CI, emulator, APK build, release or tag was started. Issues stay open pending the appropriate runtime validation; current task still includes remaining backlog work.


## Manual validation and motion follow-up

- #11: set lint abortOnError=true; remove the unreachable PR-only changed-line gate from manual CI. Upload lint reports even after failure. Historical lint errors will now correctly fail an authorized run; no suppressions or automatic trigger were added.
- #27: repeating decoration uses a shared policy-aware adapter, returning stable initial values without constructing InfiniteTransition when effective reduced motion is active. Existing component-specific static skeleton alpha overrides are preserved. This also covers equivalent patterns in Home, onboarding, Spotlight and walkthroughs. Device toggling and animation-clock checks remain outstanding.
# Follow-up fixes on main

- Removed two startup `runBlocking(Dispatchers.IO)` Room reads from `MusicTrackingService.initializeDependencies`; the existing asynchronous cache initialization and flows remain responsible for loading those preferences.
- Home now refreshes date-scoped cards on local midnight and when the app resumes after a date/time-zone change, so the weekly chart is not dependent on a new listening event to advance.
- Backup restore now caps the decompressed `data.json` stream at 1 GiB and rejects more than 5 million listening events or 2 million archive rows.
- Musicolet ZIP members are streamed through bounded decryption into private cache files instead of holding ciphertext and a second plaintext copy in memory; the 256 MiB per-member and total archive budgets remain enforced.
- Removed the browser extension's `<all_urls>` required host grant; paired-device access remains controlled by the existing local-server and optional-origin permission flow.

These changes received source review and whitespace checks only. No Android build, tests, workflow, or release were run.

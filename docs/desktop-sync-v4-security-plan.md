# Proposed desktop sync protocol v4 (planning only)

## Motivation

The current Android `DesktopSatelliteServer` and the browser extension send requests over `http://<phone-ip>:8765`. POST bodies are authenticated with HMAC + timestamp + nonce and are encrypted with AES-GCM when the extension uses the encrypted path. **However, the reusable bearer credential is still carried in the `Authorization` header over HTTP.** A passive LAN observer can capture that credential even without reading an encrypted request body. The existing replay nonce helps with recorded requests but does not protect a credential exposed in an HTTP header.

This plan is a design proposal, **not** an implemented or certified security guarantee. Shipping v4 requires coordinated Android + browser-extension versions and tested compatibility.

## Protocol goals

1. Stop transmitting reusable pairing secrets, bearer tokens or derived API keys over cleartext HTTP.
2. Bind authenticated requests to HTTP method, normalized route, timestamp, nonce and raw request-body bytes, not just the body.
3. Authenticate responses too; limit replay, request forgery, unauthorized pairing and downgrade.
4. Keep existing paired users functional through an explicit, finite migration period.
5. Avoid transmitting title/artist/album listening content before request authentication.

## Proposed handshake

- On QR pairing, exchange an authenticated ephemeral ECDH public key and a one-time pairing challenge. Show a short confirmation code on both devices for user approval. Rotate session secrets and disallow reuse of a previous challenge.
- Persist a random public **pairing ID** (not a secret) with per-device encrypted session key material in Android Keystore-backed storage. The browser holds its matching key via its secure extension storage mechanism.
- Each v4 request sends `X-Tempo-Pairing-Id`, `X-Tempo-Timestamp`, `X-Tempo-Nonce`, `X-Tempo-Signature`, `X-Tempo-Protocol: 4`; **no Bearer header**. Use HKDF-separated signing and encryption keys from the approved pairing secret.
- Canonical signed payload: protocol version, HTTP method, normalized path, byte length, SHA-256 digest of *raw encrypted body*, timestamp, nonce and pairing ID. Verify in constant time, check bounded timestamp skew, and use atomic nonce insertion per pairing.
- Encode body as an authenticated encryption envelope (e.g. AES-GCM with new random IV for each message, associated data including method/path/version/pairing ID). Include authenticated responses with request nonce or a request identifier to prevent response substitution.
- Cap ciphertext and decompressed plaintext sizes. Validate origin restrictions, rate limits and authorization *before* importing any plays or other data. The ping used for discovering phones should expose no sensitive metadata before authentication.

## Downgrade and migration policy

- Negotiate a protocol version during re-pairing. Once a device has a v4 pairing key, never silently downgrade it to v2/v3 if authentication fails.
- Support legacy paired devices only for a documented transition window with an explicit compatibility toggle and a warning that local HTTP transport does not protect credentials in transit. Do **not** send the secret in a header while using a v4 session.
- Feature-flag both clients, test old-extension/new-phone and new-extension/old-phone behavior, and migrate users only after successful v4 user confirmation.
- Device identity, stored tokens, QR formats, API paths, encryption keys and browser store IDs must be treated as migration contracts.

## Security validation before release

- Automated forged signature, replay, stale/future timestamp, nonce collision, pairing ID substitution, route/method substitution, truncated GCM tag, malformed ciphertext, oversized/compression-bomb body, fake mDNS/hotspot discovery, downgrade and re-pairing tests
- LAN packet capture during normal authenticated sync confirms no reusable credential is transmitted, and track content remains encrypted
- Multi-device pairing, key rotation, revocation, reconnect on IP change, browser restart and Android process death
- Benchmark latency, power use and maximum in-flight requests on actual devices

**Do not implement this migration without an explicitly authorized joint testing build.**

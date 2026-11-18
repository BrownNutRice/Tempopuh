#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "::error::$*" >&2
  exit 1
}

expected_certificate() {
  local fingerprint="${TEMPO_BETA_SIGNER_SHA256:-}"
  if [[ ! "$fingerprint" =~ ^[[:xdigit:]]{64}$ && ! "$fingerprint" =~ ^([[:xdigit:]]{2}:){31}[[:xdigit:]]{2}$ ]]; then
    fail 'Set TEMPO_BETA_SIGNER_SHA256 to the SHA-256 certificate fingerprint of the established beta signer.'
  fi
  fingerprint="${fingerprint//:/}"
  printf '%s\n' "${fingerprint,,}"
}

case "${1:-}" in
  check-config)
    for name in TEMPO_DEBUG_KEYSTORE_BASE64 TEMPO_DEBUG_STORE_PASSWORD TEMPO_DEBUG_KEY_ALIAS TEMPO_DEBUG_KEY_PASSWORD; do
      [[ -n "${!name:-}" ]] || fail "Beta publication requires the $name secret."
    done
    expected_certificate >/dev/null
    ;;
  verify)
    [[ $# -eq 3 ]] || fail 'Usage: beta-signing.sh verify APKSIGNER APK'
    expected="$(expected_certificate)"
    # Capture verification and certificate output from the same command. A failed
    # verification must never be mistaken for an empty or partial certificate list.
    report="$(LC_ALL=C "$2" verify --print-certs "$3")" || fail 'APK signature verification failed.'
    mapfile -t certificates < <(sed -nE 's/^Signer #[0-9]+ certificate SHA-256 digest: ([[:xdigit:]]{64})$/\1/p' <<< "$report")
    if [[ "${#certificates[@]}" -ne 1 || "${certificates[0],,}" != "$expected" ]]; then
      fail 'APK must have exactly one signer matching TEMPO_BETA_SIGNER_SHA256.'
    fi
    ;;
  *)
    fail 'Usage: beta-signing.sh check-config | verify APKSIGNER APK'
    ;;
esac

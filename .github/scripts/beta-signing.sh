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
    # Build-tools versions emit either "Signer #1" or a line per verified
    # scheme such as "V2 Signer". A scheme may contain exactly one signer,
    # and every reported signer must use the established certificate.
    declare -A schemes=()
    found=0
    while IFS= read -r line; do
      scheme=""
      certificate=""
      if [[ "$line" =~ ^Signer\ \#([0-9]+)\ certificate\ SHA-256\ digest:\ ([[:xdigit:]]{64})$ ]]; then
        [[ "${BASH_REMATCH[1]}" == 1 ]] || fail 'APK has multiple signers.'
        scheme="standard"
        certificate="${BASH_REMATCH[2]}"
      elif [[ "$line" =~ ^(V[1-4])\ Signer:\ certificate\ SHA-256\ digest:\ ([[:xdigit:]]{64})$ ]]; then
        scheme="${BASH_REMATCH[1]}"
        certificate="${BASH_REMATCH[2]}"
      elif [[ "$line" =~ ^(V[1-4])\ Signer\ \#([0-9]+):\ certificate\ SHA-256\ digest:\ ([[:xdigit:]]{64})$ ]]; then
        [[ "${BASH_REMATCH[2]}" == 1 ]] || fail 'APK has multiple signers.'
        scheme="${BASH_REMATCH[1]}"
        certificate="${BASH_REMATCH[3]}"
      elif [[ "$line" == *"certificate SHA-256 digest:"* ]]; then
        fail 'Unrecognized signer certificate output.'
      else
        continue
      fi
      [[ -z "${schemes[$scheme]:-}" ]] || fail 'APK has multiple signers for one signature scheme.'
      schemes[$scheme]=1
      [[ "${certificate,,}" == "$expected" ]] || fail 'APK signer does not match TEMPO_BETA_SIGNER_SHA256.'
      found=1
    done <<< "$report"
    [[ "$found" == 1 ]] || fail 'APK signer certificate was not reported.'
    ;;
  *)
    fail 'Usage: beta-signing.sh check-config | verify APKSIGNER APK'
    ;;
esac

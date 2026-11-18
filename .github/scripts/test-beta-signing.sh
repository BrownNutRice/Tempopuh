#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
export TEMPO_BETA_SIGNER_SHA256=fa14beee34664d2c41bc5db00f630b564992da649fa66cb4e62c523915c95407
export SIGNER_REPORT="$tmp_dir/report"
cat > "$tmp_dir/apksigner" <<'EOF'
#!/usr/bin/env bash
cat "$SIGNER_REPORT"
EOF
chmod +x "$tmp_dir/apksigner"
verify() { bash "$script_dir/beta-signing.sh" verify "$tmp_dir/apksigner" unused; }

printf 'Signer #1 certificate SHA-256 digest: %s\n' "$TEMPO_BETA_SIGNER_SHA256" > "$SIGNER_REPORT"
verify
printf 'V2 Signer: certificate SHA-256 digest: %s\n' "$TEMPO_BETA_SIGNER_SHA256" > "$SIGNER_REPORT"
verify
printf 'V3 Signer: certificate SHA-256 digest: %s\n' "$TEMPO_BETA_SIGNER_SHA256" >> "$SIGNER_REPORT"
verify
printf 'V2 Signer: certificate SHA-256 digest: %s\n' "$TEMPO_BETA_SIGNER_SHA256" >> "$SIGNER_REPORT"
if verify; then echo "Duplicate signer unexpectedly accepted" >&2; exit 1; fi
printf 'V2 Signer #2: certificate SHA-256 digest: %s\n' "$TEMPO_BETA_SIGNER_SHA256" > "$SIGNER_REPORT"
if verify; then echo "Second signer unexpectedly accepted" >&2; exit 1; fi
printf 'V2 Signer: certificate SHA-256 digest: %064d\n' 0 > "$SIGNER_REPORT"
if verify; then echo "Wrong signer unexpectedly accepted" >&2; exit 1; fi
: > "$SIGNER_REPORT"
if verify; then echo "Missing signer unexpectedly accepted" >&2; exit 1; fi
echo "Signing verification fixtures passed."

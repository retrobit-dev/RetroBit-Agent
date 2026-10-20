#!/usr/bin/env bash
# Generates a fresh release keystore OUTSIDE the repository and prints the
# GitHub secret commands needed to sign with it in CI.
#
# Why outside: the current demo key lives in signing/ and is therefore public.
# Rotating it means moving the new key out of git and into repository secrets,
# otherwise the replacement is public too and the rotation buys nothing.
#
# Usage:  bash scripts/rotate-keystore.sh [output-dir]
set -euo pipefail

OUT_DIR=${1:-$HOME/retrobit-signing}
KEYSTORE="$OUT_DIR/retrobit-release.p12"
ALIAS="retrobit"

command -v keytool >/dev/null || {
  echo "keytool tidak ditemukan. Pasang JDK 17 dulu." >&2
  exit 1
}

if [ -e "$KEYSTORE" ]; then
  echo "Keystore sudah ada: $KEYSTORE" >&2
  echo "Pindahkan atau hapus dulu supaya tidak menimpa kunci yang mungkin sudah dipakai." >&2
  exit 1
fi

mkdir -p "$OUT_DIR"

random_password() {
  # 32 URL-safe characters from the kernel CSPRNG. The read is bounded and the
  # slice happens in bash: `tr </dev/urandom | head -c 32` dies of SIGPIPE under
  # pipefail, which killed this script before it ever produced a keystore.
  local raw
  raw=$(head -c 4096 /dev/urandom | LC_ALL=C tr -dc 'A-Za-z0-9')
  printf '%s' "${raw:0:32}"
}

STORE_PASS=$(random_password)
# PKCS12 stores one password for the whole container; keytool warns and ignores
# a separate -keypass. Reusing the store password keeps RELEASE_KEY_PASSWORD
# truthful instead of pointing at a password the keystore never had.
KEY_PASS=$STORE_PASS

keytool -genkeypair \
  -keystore "$KEYSTORE" \
  -storetype PKCS12 \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass "$STORE_PASS" \
  -keypass "$KEY_PASS" \
  -dname "CN=RetroBit, OU=Release, O=RetroBit, L=Jepara, ST=Central Java, C=ID"

chmod 600 "$KEYSTORE"
base64 -w0 "$KEYSTORE" > "$OUT_DIR/keystore.b64"

cat <<EOF

Keystore baru : $KEYSTORE
Base64        : $OUT_DIR/keystore.b64
Password      : $STORE_PASS
Alias         : $ALIAS

(PKCS12 memakai satu password untuk keystore dan kunci. Untuk secret kedua,
RELEASE_KEYSTORE_PASSWORD dan RELEASE_KEY_PASSWORD diisi nilai yang sama.)

Simpan password di tempat aman. Kalau hilang, APK berikutnya tidak bisa
memperbarui yang sudah terpasang.

Langkah berikutnya — unggah sebagai secret (butuh izin "secrets: write"):

  gh secret set RELEASE_KEYSTORE_BASE64 < "$OUT_DIR/keystore.b64"
  gh secret set RELEASE_KEYSTORE_PASSWORD --body "$STORE_PASS"
  gh secret set RELEASE_KEY_ALIAS --body "$ALIAS"
  gh secret set RELEASE_KEY_PASSWORD --body "$KEY_PASS"

Setelah keempat secret ada, CI menandatangani dengan kunci baru tanpa
menyimpan apa pun di repo.

PERINGATAN: kunci baru = identitas baru. APK yang sudah terpasang dengan kunci
lama tidak bisa di-update, harus di-uninstall dulu. Jangan hapus signing/ lama
sebelum itu diumumkan.

Setelah secret aktif, hapus kunci demo dari git:

  git rm signing/retrobit-release.p12 signing/keystore.properties
  git commit -m "Remove the public demo signing key"

EOF

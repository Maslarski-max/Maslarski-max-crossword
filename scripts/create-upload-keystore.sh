#!/usr/bin/env bash
# Creates a Play upload key and a git-ignored keystore.properties next to it.
# Usage: scripts/create-upload-keystore.sh [path/to/upload.jks] [alias]
set -euo pipefail

cd "$(dirname "$0")/.."
STORE="${1:-upload-keystore.jks}"
ALIAS="${2:-upload}"

if [[ -e "$STORE" ]]; then
  echo "$STORE already exists; refusing to overwrite." >&2
  exit 1
fi

read -r -s -p "Keystore password (min 6 chars): " STORE_PASS; echo
read -r -s -p "Confirm password: " CONFIRM; echo
[[ "$STORE_PASS" == "$CONFIRM" ]] || { echo "Passwords differ." >&2; exit 1; }

keytool -genkeypair -v \
  -keystore "$STORE" -storetype PKCS12 \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$STORE_PASS" -keypass "$STORE_PASS" \
  -dname "CN=Crossword Upload Key"

umask 177
cat > keystore.properties <<PROPS
storeFile=$STORE
storePassword=$STORE_PASS
keyAlias=$ALIAS
keyPassword=$STORE_PASS
PROPS

echo "Created $STORE and keystore.properties (both git-ignored). Back them up somewhere safe."

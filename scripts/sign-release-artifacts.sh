#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-"$HOME/Library/Android/sdk"}"
KEYSTORE="${WW_KEYSTORE:-"$HOME/Development/wonderwhisper.jks"}"
KEY_ALIAS="${WW_KEY_ALIAS:-key0}"
KEYCHAIN_ACCOUNT="${WW_KEYCHAIN_ACCOUNT:-$USER}"
STOREPASS_SERVICE="${WW_STOREPASS_SERVICE:-WonderWhisper upload store password}"
KEYPASS_SERVICE="${WW_KEYPASS_SERVICE:-WonderWhisper upload key password}"

VERSION_NAME="$(awk -F'"' '/versionName[[:space:]]*=/{ print $2; exit }' "$ROOT_DIR/app/build.gradle.kts")"
VERSION_CODE="$(awk -F'= *' '/versionCode[[:space:]]*=/{ gsub(/[^0-9]/, "", $2); print $2; exit }' "$ROOT_DIR/app/build.gradle.kts")"
VERSION_NAME="${VERSION_NAME:-unknown}"
VERSION_CODE="${VERSION_CODE:-0}"

usage() {
  cat <<'USAGE'
Usage:
  scripts/sign-release-artifacts.sh aab [input.aab] [output.aab]
  scripts/sign-release-artifacts.sh apk [input-unsigned.apk] [output-signed.apk]

Environment overrides:
  WW_KEYSTORE             Keystore path. Default: ~/Development/wonderwhisper.jks
  WW_KEY_ALIAS            Key alias. Default: key0
  WW_KEYCHAIN_ACCOUNT     Keychain account. Default: current macOS user
  WW_STOREPASS_SERVICE    Keychain service name for store password
  WW_KEYPASS_SERVICE      Keychain service name for key password
  ANDROID_HOME            Android SDK path
USAGE
}

keychain_password() {
  local service="$1"
  security find-generic-password -a "$KEYCHAIN_ACCOUNT" -s "$service" -w
}

latest_build_tool() {
  local version
  version="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; \
    | sort -t. -k1,1n -k2,2n -k3,3n \
    | tail -n 1)"
  echo "$ANDROID_HOME/build-tools/$version"
}

require_file() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    echo "Missing file: $file" >&2
    exit 1
  fi
}

MODE="${1:-}"
if [[ -z "$MODE" || "$MODE" == "-h" || "$MODE" == "--help" ]]; then
  usage
  exit 0
fi

require_file "$KEYSTORE"

STOREPASS="$(keychain_password "$STOREPASS_SERVICE")"
KEYPASS="$(keychain_password "$KEYPASS_SERVICE")"
if [[ -z "$KEYPASS" ]]; then
  KEYPASS="$STOREPASS"
fi

case "$MODE" in
  aab)
    INPUT="${2:-"$ROOT_DIR/app/build/outputs/bundle/release/app-release.aab"}"
    OUTPUT="${3:-"$ROOT_DIR/app/build/outputs/bundle/release/WonderWhisper-${VERSION_NAME}-v${VERSION_CODE}-signed.aab"}"
    require_file "$INPUT"
    cp "$INPUT" "$OUTPUT"
    WW_STOREPASS="$STOREPASS" WW_KEYPASS="$KEYPASS" jarsigner \
      -keystore "$KEYSTORE" \
      -storepass:env WW_STOREPASS \
      -keypass:env WW_KEYPASS \
      -signedjar "$OUTPUT" \
      "$OUTPUT" "$KEY_ALIAS"
    jarsigner -verify -verbose -certs "$OUTPUT" >/dev/null
    ;;
  apk)
    BUILD_TOOL_DIR="$(latest_build_tool)"
    ZIPALIGN="$BUILD_TOOL_DIR/zipalign"
    APKSIGNER="$BUILD_TOOL_DIR/apksigner"
    INPUT="${2:-"$ROOT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"}"
    OUTPUT="${3:-"$ROOT_DIR/app/build/outputs/apk/release/WonderWhisper-${VERSION_NAME}-v${VERSION_CODE}-signed.apk"}"
    ALIGNED="${OUTPUT%.apk}-aligned.apk"
    require_file "$INPUT"
    "$ZIPALIGN" -p -f 4 "$INPUT" "$ALIGNED"
    WW_STOREPASS="$STOREPASS" WW_KEYPASS="$KEYPASS" "$APKSIGNER" sign \
      --ks "$KEYSTORE" \
      --ks-key-alias "$KEY_ALIAS" \
      --ks-pass env:WW_STOREPASS \
      --key-pass env:WW_KEYPASS \
      --out "$OUTPUT" \
      "$ALIGNED"
    "$APKSIGNER" verify --verbose --print-certs "$OUTPUT"
    rm -f "$ALIGNED"
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac

unset STOREPASS KEYPASS WW_STOREPASS WW_KEYPASS
echo "Signed artifact: $OUTPUT"

#!/usr/bin/env bash
# Builds the F-Droid APK (foss flavor, one universal APK) the same way F-Droid does,
# then signs it with the release key so F-Droid can verify it and ship our signature.
#
#   scripts/build-foss-apk.sh 1.7.1          # builds tag v1.7.1
#   scripts/build-foss-apk.sh 1.7.1 HEAD     # builds another ref, for testing
#
# Output: release/Apexy-<version>-foss.apk. Needs JAVA_HOME (JDK 21, like F-Droid's
# build server) and the Android SDK in ANDROID_HOME or local.properties.
set -euo pipefail

version="${1:?usage: $0 <version> [git-ref]}"
ref="${2:-v$version}"
root="$(cd "$(dirname "$0")/.." && pwd)"
out="$root/release/Apexy-$version-foss.apk"

sdk="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' "$root/local.properties" 2>/dev/null)}"
[ -d "$sdk" ] || { echo "Android SDK not found: set ANDROID_HOME" >&2; exit 1; }
export ANDROID_HOME="$sdk"
apksigner="$(ls -d "$sdk"/build-tools/* | sort -V | tail -1)/apksigner"

# A fresh clone has no keystore.properties, so Gradle leaves the APK unsigned, as on F-Droid.
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
git clone --quiet "$root" "$work/src"
git -C "$work/src" checkout --quiet "$(git -C "$root" rev-parse "$ref^{commit}")"
(cd "$work/src" && ./gradlew --quiet --no-daemon assembleFossRelease -PnoAbiSplits)
unsigned="$work/src/app/build/outputs/apk/foss/release/app-foss-release-unsigned.apk"

# Read the key from keystore.properties without putting passwords on the command line.
prop() { sed -n "s/^$1=//p" "$root/keystore.properties"; }
store="$(prop storeFile)"
[[ "$store" = /* ]] || store="$root/app/$store"
export APEXY_KS_PASS="$(prop storePassword)" APEXY_KEY_PASS="$(prop keyPassword)"

# --alignment-preserved and no v1/v4: F-Droid copies this signature onto its own build,
# which only works if signing changes nothing but the signature block.
mkdir -p "$root/release"
"$apksigner" sign --alignment-preserved \
    --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
    --ks "$store" --ks-key-alias "$(prop keyAlias)" \
    --ks-pass env:APEXY_KS_PASS --key-pass env:APEXY_KEY_PASS \
    --out "$out" "$unsigned"
unset APEXY_KS_PASS APEXY_KEY_PASS

"$apksigner" verify --print-certs "$out" | grep "certificate SHA-256"
echo "Built $out"

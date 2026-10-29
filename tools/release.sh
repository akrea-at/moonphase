#!/usr/bin/env bash
# Builds the tagged release from a clean clone (as F-Droid does), signs it and attaches it to the GitHub release.
# F-Droid verifies reproducibility by copying this signature onto its own unsigned build, so the APK is signed
# without v1 and with the original zip alignment.
set -euo pipefail

version=${1:?usage: tools/release.sh <version>, e.g. 1.0.1}
tag=v$version
keystore=${MOONPHASE_KEYSTORE:-$HOME/keys/moonphase-release.jks}
alias=${MOONPHASE_KEY_ALIAS:-moonphase}
repo=$(git rev-parse --show-toplevel)
sdk=${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' "$repo/local.properties")}
apksigner=$(ls -d "$sdk"/build-tools/*/ | sort -V | tail -1)apksigner

git -C "$repo" rev-parse -q --verify "refs/tags/$tag" >/dev/null || { echo "Tag $tag not found." >&2; exit 1; }
[ -f "$keystore" ] || { echo "Keystore $keystore not found (set MOONPHASE_KEYSTORE)." >&2; exit 1; }

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
git clone -q --no-checkout "$repo" "$work/src"
git -C "$work/src" -c advice.detachedHead=false checkout -q "$tag"
(cd "$work/src" && ANDROID_HOME=$sdk ./gradlew -q --no-daemon assembleRelease)

mkdir -p "$repo/dist"
apk=$repo/dist/moonphase-$version.apk
"$apksigner" sign --v1-signing-enabled false --alignment-preserved true \
    --ks "$keystore" --ks-key-alias "$alias" --out "$apk" \
    "$work/src/app/build/outputs/apk/release/app-release-unsigned.apk"
"$apksigner" verify --print-certs "$apk" | grep 'SHA-256'

code=$(git -C "$repo" show "$tag:app/build.gradle.kts" | sed -n 's/.*versionCode = \([0-9]*\).*/\1/p')
notes=$(git -C "$repo" show "$tag:fastlane/metadata/android/en-US/changelogs/$code.txt")
if gh release view "$tag" >/dev/null 2>&1; then
    gh release upload "$tag" "$apk" --clobber
else
    gh release create "$tag" "$apk" --verify-tag --title "$version" --notes "$notes"
fi
echo "Released $apk"

#!/usr/bin/env bash
# Builds the signed release APK and publishes it as a GitHub Release, which the in-app updater
# picks up. Usage: scripts/release.sh <notes.md>   (notes: Chinese markdown, shown in the app)
#
# Before running: raise versionCode and versionName in app/build.gradle.kts and commit.
# Must run on the machine whose ~/.android/debug.keystore signed the installed app.
set -euo pipefail
cd "$(dirname "$0")/.."

NOTES=${1:?usage: scripts/release.sh <notes.md>}
GH=${GH:-gh}
REPO=$(sed -n 's/^jizhang.updateRepo=//p' gradle.properties)
VERSION=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
TAG="v$VERSION"
APK="dist/jizhang-$VERSION.apk"

[ -n "$REPO" ] || { echo "jizhang.updateRepo is empty in gradle.properties" >&2; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "Commit your changes first." >&2; exit 1; }
if "$GH" release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
    echo "$TAG is already released; raise versionName/versionCode first." >&2
    exit 1
fi

JAVA_HOME=${JAVA_HOME:-/f/AndroidStudio/jbr} ./gradlew :app:testDebugUnitTest :app:assembleRelease -q
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk "$APK"

git rev-parse -q --verify "refs/tags/$TAG" >/dev/null || git tag -a "$TAG" -m "小账本 $TAG"
git push origin HEAD "$TAG"
"$GH" release create "$TAG" "$APK" --repo "$REPO" --title "小账本 $TAG" --notes-file "$NOTES" --verify-tag
echo "Published $TAG: https://github.com/$REPO/releases/tag/$TAG"

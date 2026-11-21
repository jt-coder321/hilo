#!/usr/bin/env bash
# Publishes a built APK to the "updates" branch, which the app's Check for updates reads.
# Usage: tools/publish_update.sh path/to/app-release.apk "What's new, one line"
set -euo pipefail
APK="$1"; NOTES="${2:-}"
REPO="jt-coder321/hilo"
BADGING=$("${ANDROID_HOME:-$HOME/android-sdk}"/build-tools/35.0.0/aapt dump badging "$APK" | sed -n 1p)
CODE=$(sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" <<<"$BADGING")
NAME=$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<<"$BADGING")
SHA=$(sha256sum "$APK" | cut -d' ' -f1)
SIZE=$(stat -c %s "$APK")
WORK=$(mktemp -d)
cp "$APK" "$WORK/HiLoTrainer.apk"
python3 - "$WORK/latest.json" "$CODE" "$NAME" "$SHA" "$SIZE" "$NOTES" "$REPO" <<'PY'
import json, sys
out, code, name, sha, size, notes, repo = sys.argv[1:]
json.dump({"versionCode": int(code), "versionName": name, "sha256": sha, "sizeBytes": int(size), "notes": notes,
           "apkUrl": f"https://raw.githubusercontent.com/{repo}/updates/HiLoTrainer.apk"}, open(out, "w"), indent=2)
PY
cd "$WORK"
git init -q -b updates
git add HiLoTrainer.apk latest.json
git -c user.name="Hi-Lo Trainer release" -c user.email="noreply@example.com" commit -qm "Version $NAME ($CODE)"
# The branch only ever holds the newest APK, so it is replaced each time instead of growing.
git push -f "https://github.com/$REPO" updates
echo "Published $NAME ($CODE) sha256 $SHA"

#!/bin/bash
# usage: tools/package-k.sh <signed jimeikin-k-<version>.apk> <signed jimeikin-full-<version>.apk>
# Music Box K goes to the InkApps portal only: nothing here publishes, tags or pushes.
# Checks the K APK is signed with the full edition's key, then writes
# ~/Desktop/dev/apps/music-box-k/release/{jimeikin-k-<version>.apk, .sha256, portal-notes.md}.
set -eu
k=${1:?K apk}; full=${2:?full apk}
here=$(cd "$(dirname "$0")/.." && pwd)
bt=$(ls -d ~/Android/Sdk/build-tools/* | sort -V | tail -1)
cert() { "$bt/apksigner" verify --print-certs "$1" | grep -m1 'SHA-256 digest' | awk '{print $NF}'; }
[ -n "$(cert "$k")" ] && [ "$(cert "$k")" = "$(cert "$full")" ] || { echo "K is not signed with the full edition's key"; exit 1; }
badging=$("$bt/aapt2" dump badging "$k" | head -1)
case "$badging" in *"name='com.wanderwildwood.jimeikin.k'"*) ;; *) echo "not the K app"; exit 1;; esac
v=$(echo "$badging" | grep -o "versionName='[^']*'" | cut -d"'" -f2)
out=~/Desktop/dev/apps/music-box-k/release; mkdir -p "$out"
cp "$k" "$out/jimeikin-k-$v.apk"; (cd "$out" && sha256sum "jimeikin-k-$v.apk" > "jimeikin-k-$v.apk.sha256")
{
  echo "# Music Box K $v - notes for the InkApps portal"; echo
  echo "- Package: com.wanderwildwood.jimeikin.k, version $v, signed with the same key as Music Box"
  echo "- File: jimeikin-k-$v.apk, sha256 $(cut -d' ' -f1 "$out/jimeikin-k-$v.apk.sha256")"
  echo "- Inside: music on the phone and card, your own Subsonic/Navidrome server, internet radio. No YouTube, no NewPipe: scanned (tools: gradle checkStoreApk)."
  echo; echo "## Store listing"; echo; cat "$here/store-edition/README.md"
  echo; echo "## Privacy policy"; echo; cat "$here/store-edition/PRIVACY.md"
  echo; echo "## Terms (as shown in About)"; echo
  grep -h '<string name="about_terms"' "$here/app/src/store/res/values/strings.xml" | sed 's/<[^>]*>//g; s/\\//g'
  echo; echo "## Screenshots"; echo; ls "$here/screenshots" 2>/dev/null | sed "s/^/- screenshots\//" || true; echo; echo "(Those show Music Box; retake them from the K APK before submitting if the portal wants the K name in them.)"
  echo; echo "Icons: final-A-ringless-512.png and -100.png in this folder (ringless, per the store rule)."
} > "$out/portal-notes.md"
echo "wrote $out"

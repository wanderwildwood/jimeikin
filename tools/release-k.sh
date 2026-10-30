#!/bin/bash
# usage: tools/release-k.sh <path to the signed jimeikin-k-<version>.apk> "release note"
# Publishes Music Box K as tag k-v<version> with assets jimeikin-k.apk and jimeikin-k.apk.sha256.
# Does not touch the full app's tags (v2.18.x). Run from the repo, tree committed.
set -eu
apk=${1:?apk}; note=${2:?note}
cd "$(git rev-parse --show-toplevel)"
[ -z "$(git status --short)" ] || { echo "tree is not clean"; exit 1; }
aapt2=$(ls ~/Android/Sdk/build-tools/*/aapt2 | tail -1)
badging=$("$aapt2" dump badging "$apk" | head -1)
case "$badging" in *"name='com.wanderwildwood.jimeikin.k'"*) ;; *) echo "not the K app"; exit 1;; esac
v=$(echo "$badging" | grep -o "versionName='[^']*'" | cut -d"'" -f2); tag=k-v$v
d=$(mktemp -d); cp "$apk" "$d/jimeikin-k.apk"; (cd "$d" && sha256sum jimeikin-k.apk > jimeikin-k.apk.sha256)
slug=$(git remote get-url origin | sed 's#.*github.com[:/]##; s#\.git$##')
git tag "$tag"; git push origin "$tag"
gh release create "$tag" -R "$slug" --verify-tag --title "Music Box K $v" --notes "$note" "$d"/*
rm -rf "$d"

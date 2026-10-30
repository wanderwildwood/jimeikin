# Releasing

One repository, two apps. **Music Box** goes to GitHub. **Music Box K does not**: it is not on
GitHub in any form (no release, no tag, no asset) and belongs only to the InkApps portal.

| | Music Box | Music Box K |
|---|---|---|
| flavour | `full` | `store` |
| application id | `com.wanderwildwood.jimeikin` | `com.wanderwildwood.jimeikin.k` |
| version line | 2.18.x (`defaultConfig`) | 1.0.x (in the `store` flavour) |
| goes to | GitHub release `v<version>` | the InkApps portal, by hand |
| built APK | `app/build/outputs/apk/full/release/jimeikin-full-<v>.apk` | `app/build/outputs/apk/store/release/jimeikin-k-<v>.apk` |

Both are signed with the key in `signing/` (never in the repo). Builds are local:
`~/legion-build.sh jimeikin` (or `~/dell-build.sh jimeikin`) builds both flavours.

## Music Box
Tag `v<versionName>` and `gh release create`, as before. `.github/workflows/release.yml`
builds only the full flavour and refuses anything of K.

## Music Box K
1. Build the signed release APK (`assembleStoreRelease`). Gradle then runs `checkStoreApk`, which
   fails the build if the APK's dex, resources or assets contain "youtube", "newpipe" or
   "schabi" in any letter case.
2. `tools/package-k.sh <jimeikin-k-<v>.apk> <jimeikin-full-<v>.apk>` verifies K is signed with the
   full edition's key and writes `~/Desktop/music-box-k-release/jimeikin-k-<v>.apk`, `.sha256`
   and `portal-notes.md` (version, what is inside, privacy and terms text, store listing text,
   screenshot list). That is all; it publishes nothing.
3. Upload to the portal by hand.

Changing the K version: `versionName` and `versionCode` in the `store` flavour only. Music Box's:
`defaultConfig` only. Never raise one to follow the other.

Any string that says "Music Box" in `app/src/main/res` needs a K twin: run `tools/brand-k.py`
(`MusicBoxKTest` fails otherwise). Store texts for K are in `store-edition/`.

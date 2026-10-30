# Releasing

One repository, two apps, two tags. They never share a tag, an asset name or a version line.

| | Music Box | Music Box K |
|---|---|---|
| flavour | `full` | `store` |
| application id | `com.wanderwildwood.jimeikin` | `com.wanderwildwood.jimeikin.k` |
| version line | 2.18.x (`versionName`/`versionCode` in `defaultConfig`) | 1.0.x (in the `store` flavour) |
| tag | `v2.18.14` | `k-v1.0.0` |
| GitHub release title | `v2.18.14` | `Music Box K 1.0.0` |
| asset | `jimeikin.apk` (+ `jimeikin-v2.18.14.apk`, `.sha256`) | `jimeikin-k.apk` (+ `.sha256`) |
| built APK | `app/build/outputs/apk/full/release/jimeikin-full-<v>.apk` | `app/build/outputs/apk/store/release/jimeikin-k-<v>.apk` |

Both are signed with the key in `signing/` (never in the repo). Builds are local:
`~/legion-build.sh jimeikin` (or `~/dell-build.sh jimeikin`) builds both flavours; check each
with `apksigner verify --print-certs` against the previous release's certificate.

Music Box: as before, tag `v<versionName>`, release it with `gh release create`.

Music Box K: `tools/release-k.sh` does it from a clean, committed tree: it reads the K
`versionName` from the built APK, tags `k-v<version>`, and creates the GitHub release
"Music Box K <version>" with `jimeikin-k.apk` and `jimeikin-k.apk.sha256`.

Changing the K version: edit `versionName` and raise `versionCode` in the `store` flavour only.
Changing Music Box's: `defaultConfig` only. Never raise one to follow the other.

Any string that says "Music Box" in `app/src/main/res` needs a K twin: run `tools/brand-k.py`
(`BrandTest` fails otherwise). Store texts for K are in `store-edition/`.

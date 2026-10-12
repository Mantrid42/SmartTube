# Feature and testing branches

`origin` is `yuliskov/SmartTube` (upstream). `fork` is `Mantrid42/SmartTube`.

| Branch | Purpose |
| --- | --- |
| `master` | Mirrors upstream `master`. |
| `feature/channel-content` | Channel-content scanning, indicators, UI settings, and the existing playback fixes. |
| `feature/dearrow-channel` | Independent per-channel DeArrow opt-in, based on upstream. |
| `testing/combined` | Merges both features for integration testing and APK builds. |

## Updating upstream and features

Start with a clean working tree. Sync the upstream mirror:

```sh
git fetch origin
git switch master
git merge --ff-only origin/master
git push fork master
```

Update each feature, resolve conflicts, and validate before pushing:

```sh
git switch feature/channel-content
git merge master
git submodule update --init --recursive
bash ./gradlew :common:testStstableDebugUnitTest
git push fork feature/channel-content

git switch feature/dearrow-channel
git merge master
git submodule update --init --recursive
bash ./gradlew :common:testStstableDebugUnitTest
git push fork feature/dearrow-channel
```

Merge the updated features into the integration branch and build:

```sh
git switch testing/combined
git merge feature/channel-content
git merge feature/dearrow-channel
git submodule update --init --recursive
bash ./gradlew :common:testStstableDebugUnitTest :smarttubetv:assembleStstableDebug
git push fork testing/combined
```

This checkout's `local.properties` points to its WSL Android SDK, so run Gradle from WSL in `/mnt/c/Codex/SmartTube`. For a native Windows SDK configuration, use `gradlew.bat` instead.

Debug APKs are written to `smarttubetv/build/outputs/apk/ststable/debug/`.

## Per-channel DeArrow

With global title and thumbnail replacement off, long-press a channel and choose **Enable DeArrow for this channel**. Its videos receive DeArrow titles and thumbnails, including subsequent pages. The selection is saved with the app profile. Long-press it again to remove the opt-in. Global replacement settings continue to apply independently.

Keep implementation commits on their feature branches; use `testing/combined` for merge resolutions and integration documentation.

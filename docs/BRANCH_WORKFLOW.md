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

Long-press a channel in the Channels list or pinned sidebar and choose **Enable DeArrow for this channel**. In DeArrow settings, **Included channels** controls original-language titles, crowdsourced titles, crowdsourced thumbnails, and fallback thumbnails independently of global activation. These options are shared by all included channels and override the global options for those channels. Titles and thumbnails default to enabled for existing opt-ins.

When any global replacement option is active, the channel menu also offers **Exclude from DeArrow**. Exclusions override both global and included-channel options, including original-language titles and fallback thumbnails. **Remove DeArrow exclusion** restores the previous behavior. Membership, exclusions, and included-channel options are saved with the app profile.

Changing channel membership or exclusion does not reload the Channels list. Select another channel and return to load its videos with the updated options.

Native Windows beta validation uses `gradlew.bat :common:testStbetaDebugUnitTest :smarttubetv:testStbetaDebugUnitTest :smarttubetv:assembleStbetaDebug` with `JAVA_HOME` set to the installed updated Java 11 runtime and `ANDROID_HOME`/`ANDROID_SDK_ROOT` set to the native Android SDK. Java 11.0.2 cannot read this machine's current debug keystore; Java 11.0.31 works. The beta APK is under `smarttubetv/build/outputs/apk/stbeta/debug/`.

Keep implementation commits on their feature branches; use `testing/combined` for merge resolutions and integration documentation.

## Menu layout

`AppDialogPresenter.appendHeading(title)` adds a bold, indented, muted heading; `appendSpacer()` adds a blank 24dp row. Neither row is selectable or focusable. DeArrow uses these to show Global Activation and Per-channel Activation options directly, followed by spacing before its status and About links.

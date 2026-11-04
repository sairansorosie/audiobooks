# Audiobooks: Release and verification

This repository contains the LightOS `:tool` audiobook application (package
`com.lightphone.audiobooks`). The original August 2026 migration from package
`com.stan.libbylight` was not in-place upgrade-compatible. Preserve this ID.

## Source finalization vs app publication

Pushing the tested MVP source and documentation to `main` is not a binary release.
No production signing, release tag, GitHub Release, or device certification is
implied. `tool/lighttool.toml` currently identifies the previous tool version;
versionCode and versionName must be intentionally reviewed for any future
installable release.

## Compatible SDK and build

The verified SDK is https://github.com/lightphone/light-sdk at 0.2.3,
commit `8d3238c83a27b25ce20641e5fe3dc7006e96814e`.
The app is the `:tool` module (the old `:app` and `:server` commands are obsolete).
Stage the complete repository `tool/` directory in the official SDK's `tool/`
workspace and invoke Gradle from the SDK root:

```bash
./gradlew :tool:clean -DlightSdk.allowAltServerPackage=true
./gradlew :tool:assembleDebug :tool:testDebugUnitTest -DlightSdk.allowAltServerPackage=true
./gradlew :tool:lintDebug -DlightSdk.allowAltServerPackage=true
./gradlew :tool:assembleRelease -DlightSdk.allowAltServerPackage=true
```

The `allowAltServerPackage` development flag is used only for the separately
configured emulator workspace. The tracked `tool/lighttool.toml` is production
metadata with `serverPackage = "com.lightos"`; never commit the emulator-only
`com.thelightphone.sdk.emulator` override. Keep SDK restrictions enabled.

## Signing and upgrade safety

The SDK development key is only for emulator/development distribution. Use a
stable, privately held signing identity for any eventual published build.
Changing an installed APK's signing key prevents in-place upgrades and can
require reinstalling, which erases application-private progress. Do not
uninstall devices to resolve a signing mismatch without explicit approval.
Do not commit keystores, credentials, or signing secrets.

## Emulator and physical-device release QA

- Existing USB and Tool Manager import paths; Sync Library from Settings
- Single-file M4B/MP3 and multi-part books; embedded chapters and navigation
- Pause/resume, 15-second skips, scrubbing, 1.75x speed and saved preference
- Auto-play-next and chapter boundary behavior, plus pause/restart and progress
- Background/detached audio, lock-screen and volume/media controls on LP3
- Metadata overrides, restore imported details, cancelled edits, long titles
- All Books/Series counts, alignment, sorting and scrollbar behavior
- Recently Added/Recently Played persistence through rescan and restart
- Mark Finished and confirmed Clear Progress, including during active audio
- Scroll return after Player/Edit, tab switching to top, compact dropdowns
- Rescan/error cases, file corruption, restart, interruptions, accessibility
- Privacy, permissions, licenses and open-source attribution

Emulator UI QA has been accepted by the owner. Physical Light Phone III tests,
release signing, a version bump, updated screenshots, and binary publication
are outstanding separate gates. Do not label this source push a public binary
release or imply physical certification.

## Backlog exclusions

The headphones Return to Last Played shortcut, in-app audiobook removal,
bookmarks, and writing embedded audio metadata are explicitly deferred.
See `docs/product/MVP_SCOPE_AND_BACKLOG_2026-11-03.md`.

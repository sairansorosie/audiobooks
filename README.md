# Audiobooks

*A minimalist audiobook player for the Light Phone III.*

<p align="center"><a href="https://ko-fi.com/fenleon">
  <picture><source media="(prefers-color-scheme: dark)" srcset="art/coffee-hand-filled-alpha-white-steam.png"><img src="art/coffee-hand-filled-alpha-white.png" alt="Hand holding Coffee" height="50" style="vertical-align: middle;"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="art/buy-me-a-coffee-alpha-white.png"><img src="art/buy-me-a-coffee-alpha-black.png" alt="Buy Me A Coffee" height="40" style="vertical-align: middle;"></picture>
  <img src="art/ok-hand-filled-alpha-white.png" alt="OK Hand" height="50" style="vertical-align: middle;"></a></p>

Play your local-only audiobooks on the Light Phone III, built using the Light SDK.


<p align="center">Support my work by leaving me a <a href="https://ko-fi.com/fenleon">tip</a> or <a href="https://github.com/sponsors/fenleon">sponsoring me</a>. A little goes a long way.</p>



## Screenshots

These existing screenshots show an earlier version of the interface; updated captures are pending.

<p align="center">
  <img src="screenshots/library.png" width="32%" alt="Library" />
  <img src="screenshots/player.png" width="32%" alt="Player" />
  <img src="screenshots/chapters.png" width="32%" alt="Chapters" />
  <img src="screenshots/settings.png" width="32%" alt="Settings" />
  <img src="screenshots/speed.png" width="32%" alt="Playback speed" />
</p>

## What it does

- One streamlined Library with Title, Author, Series, Recently Added and Recently Played sorting.
- Book Options is a text-only screen for editing details, finishing or clearing progress.

- Plays audiobooks stored on the phone. Books stay on your device.
- One audio file = one book. One folder of audio files = one book, played in order (name your files 01, 02, 03… to be safe).
- Chapters: folder books list their files, single-file books use their embedded chapter tags (MP3 chapter frames, M4B bookmarks).
- Remembers where you left off.
- Library views for All Books and Series, with optional series positions and a Standalone Books section.
- Local-only edits to title, author, series name and position; original audiobook tags stay unchanged.
- Sort All Books by title, author, recently added, or recently played.
- Mark a book as Finished or Clear Progress without deleting its audio files.
- Playback speed choices 0.5x, 1x, 1.25x, 1.5x, 1.75x, and 2x from a compact Player menu, and an Auto-Play toggle for chapter advancement.
- After a pause longer than 5 minutes, playback jumps back 15 seconds so you re-orient.
- Plays in the background, with a media notification and lockscreen controls.
- Formats: MP3, M4B, M4A, AAC, OGG, OGA, OPUS, FLAC, WAV.

## Getting your books on the phone

1. Connect your Light Phone III to a computer and open shared storage.
2. Make a folder called `Audiobooks` (if it isn't there already).
3. Copy one audio file directly into it (that's a book), or make a folder per book and put its files inside.
4. Open Audiobooks. Select **Settings → Sync Library** to rescan local audiobook files. This does not access cloud storage.

## Limitations

- To remove a book, delete its files on the device; there is no in-app deletion.
- The headphones shortcut for Return to Last Played, bookmarks, and writing edited details back to embedded audio tags are post-MVP backlog items.
- Existing metadata overrides, playback history, and first-discovered timestamps are stored locally on the device.
- Light Phone III physical-device regression testing is still required before a binary release.

## Building it yourself

The current tool uses the [official Light SDK](https://github.com/lightphone/light-sdk), verified at SDK 0.2.3 (commit `8d3238c8`), plus JDK 17/21 and the Android SDK compatible with that checkout. The application is the `:tool` module, not the earlier `:app` module. In the SDK workspace, stage this repository's complete `tool/` directory into the SDK's `tool/` module and run the commands below. The local Android emulator needs a `serverPackage` override; the tracked production file must retain `com.lightos`.

```bash
./gradlew :tool:assembleDebug :tool:testDebugUnitTest :tool:lintDebug
```

Release signing: see `RELEASE.md`.

## Privacy

No analytics, no ads, no accounts, no telemetry. Your books never leave the device.

## Legal
Started as a fork of [Bard](https://github.com/sjkornelsen/bard), rebuilt for local-only playback.

Unofficial, independent open-source project — not affiliated with or endorsed by The Light Phone, Inc. Light Phone and Light OS are trademarks of The Light Phone, Inc.

MIT licensed — see [LICENSE](LICENSE). Includes resources from the Light SDK, noted in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

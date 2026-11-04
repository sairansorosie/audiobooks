# Light Audiobooks: MVP scope and post-MVP backlog

Updated: 2026-11-03. This document supersedes the earlier phase plan where it
conflicts with approved user decisions. It does not replace the original PRD.

## MVP implemented and owner-QA accepted

- Local audiobook discovery through the existing USB and Tool Manager file flow; Settings > Sync Library manually scans local storage
- All Books and conditional Series with Standalone Books, series number display, metadata override editor, and the compact monochrome layout
- Title A-Z, Author A-Z, Recently Added (first detected), and Recently Played (most recent successful play/resume) sorting
- Persistent listening position and separate Finished state; Mark as Finished and confirmed Clear Progress
- Shared book action menus and Player controls; anchored dropdowns, current selections underlined, and 1.75x playback speed
- Audio focus, detached playback, chapters, file transfer, and background listening retained from the existing application

## Intentionally not part of MVP

- Original Phase 10: Return to Last Played headphones shortcut, deferred
- Original Phase 11: Remove Audiobook, skipped; removal is by deleting files outside the app
- Bookmarks, deferred
- Editing embedded MP3/M4B tags in the original audio files, deferred; edits are app-local overrides
- Cloud sync, cover art UI, accounts, analytics, and unrelated expansions

## Known limitations and release gates

- A book's identity is based on its source/path; moving/renaming can create a new identity
- Pre-existing books had no historical added timestamps before first-discovery tracking
- Recently Played is populated by successful play/resume after tracking was implemented
- Some album fields include a literary series, but the app does not infer series membership from album/title or overwrite manual edits
- Existing screenshots are from an earlier UI and should be refreshed before formal publication
- Build, unit tests and Android lint must pass in the official LightOS SDK workspace pinned to 0.2.3 (commit 8d3238c8)
- Emulator QA is owner-accepted; physical Light Phone III regression, distribution signing, and release packaging remain separate approval gates
- Development emulator uses a local-only `serverPackage` override. The published source keeps `serverPackage = "com.lightos"`.
- Upstream project authorship, copyright notices, and LICENSE remain in place

## Publication policy

Merge/push of tested source to `main` is authorized by the project owner.
Do not publish a binary, create a release tag, or claim physical-device certification
without separate verification and approval.

# Library Simplification PRD v2.1

Status: approved by product owner, implementation branch pending emulator QA.

## Purpose
One text-first audiobook Library, five sorting choices and one shared Book Options screen.
Replace redundant Series navigation and decorative action icons without affecting audio playback,
book data, file transfer or the existing book-row presentation.

## Requirements

- Exactly one Library view: remove the All Books/Series tabs and Series-only page.
- Show every audiobook exactly once, with the existing total book count and sort selector.
- **BookRow is unchanged** in source, appearance and behavior for all five sorts.
- Sort choices in order: Title A-Z, Author A-Z, Series A-Z, Recently Added, Recently Played.
- Preserve the existing four algorithms and saved preferences. New installs default to Title A-Z.
- Series A-Z: assigned series before standalone; normalize series names by trim and casefold;
  series alphabetically; numeric nonnegative series position ascending (decimal supported);
  missing/invalid positions after numbered and by title; standalone last by title;
  equal titles by stable source and book ID. No automatic series inference.
- Sort changes scroll to top; opening and dismissing a dropdown does not. Returning from
  Player, Book Options or editing preserves scroll when supported.
- Existing Library and Player three-dot controls open the same dedicated Book Options screen.
- Top bar: Back and Book Options; below: small centered secondary title (up to 2 lines,
  full accessibility description); below: text-only actions with no leading icons.
- Actions: Edit Book Details always; Mark as Finished if unfinished; Clear Progress if
  position is nonzero or finished. No in-app Remove Audiobook.
- Edit Save returns to the origin and refreshes displayed metadata; Edit Cancel returns to
  Book Options; unsaved changes retain editor confirmation.
- Mark saves immediately without affecting audio. On success return to origin; on failure
  remain with an error.
- Clear keeps the existing confirmation and safe source-specific audio reset; success
  returns to origin; failure stays visible. Audio files, overrides and play history survive.
- On Book Options Back return to origin without changing playback. Do not create a new Player.
- Retire the legacy LibraryViewPreferenceStore code without deleting users' old preference files.
- Preserve SDK detached audio, settings, chapter navigation, privacy and third-party notices.

## Test and release criteria

- Series decimal sort, name normalization, missing positions, identity ties, standalone,
  metadata override and empty-series behavior are unit-tested.
- All old sorting paths retain their previous results.
- Library rows remain byte-identical in source. No special rows, headings or decorators.
- Both entry points, Save/Cancel, Finished and Clear must be emulator-QA tested.
- Background playback must continue through navigation; Player must reuse its current handle
  when clearing progress; other audiobook queues must be untouched.
- Full debug unit test, SDK lint and release build required before branch publication.
- No push to main, merge, tag, or production release without separate approval.

package com.lightphone.audiobooks.screens

import com.lightphone.audiobooks.library.Audiobook

/** Library-only percentage. Playback and the Player's chapter scrubber are unchanged. */
internal fun libraryProgressPercent(book: Audiobook): Int? {
    if (book.completed || book.durationMilliseconds <= 0L) return null
    // Keep the existing Library calculation based on whole-book position/duration.
    val progress = book.positionMilliseconds.toFloat() / book.durationMilliseconds
    val percent = (progress.coerceIn(0f, 1f) * 100).toInt().coerceAtMost(99)
    return percent.takeIf { it >= 1 }
}

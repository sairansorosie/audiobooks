package com.lightphone.audiobooks

import com.lightphone.audiobooks.library.Audiobook
import com.lightphone.audiobooks.library.AudiobookPart
import com.lightphone.audiobooks.library.EmbeddedChapter

/** Formats a duration in milliseconds as m:ss or h:mm:ss. */
fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}


/**
 * Estimated wall-clock listening time left in the *currently displayed chapter*.
 * The player already derives chapter-scoped position/duration for its timestamps.
 * Dividing unplayed media time by the current speed yields real listening time.
 * Returns null if duration or speed is unknown; never invent a duration.
 * Round up to minutes so a positive remainder never appears as zero minutes.
 */
fun formatChapterTimeRemaining(durationMs: Long, positionMs: Long, speed: Float): String? {
    if (durationMs <= 0L || !speed.isFinite() || speed <= 0f) return null
    val unplayedMs = durationMs - positionMs.coerceIn(0L, durationMs)
    val totalMinutes = kotlin.math.ceil(unplayedMs.toDouble() / speed.toDouble() / 60_000.0).toLong()
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}h ${minutes}m left" else "${minutes}m left"
}

/**
 * Estimated wall-clock time remaining, adjusted for playback speed.
 * Duration and position are media milliseconds, never wall-clock milliseconds.
 * Returns null for unknown duration or invalid speed rather than guessing.
 * Under an hour, show a seconds-resolution countdown (floor, except that
 * any positive sub-second remainder shows 1s). At one hour or more, round
 * the estimate up to whole minutes.
 */
fun formatRemainingListeningTime(durationMs: Long, positionMs: Long, speed: Float): String? {
    if (durationMs <= 0L || !speed.isFinite() || speed <= 0f) return null
    val unplayedMs = durationMs - positionMs.coerceIn(0L, durationMs)
    val realSeconds = unplayedMs.toDouble() / speed.toDouble() / 1_000.0
    if (!realSeconds.isFinite() || realSeconds >= Long.MAX_VALUE.toDouble()) return null
    if (realSeconds >= 3_600.0) {
        val minutes = kotlin.math.ceil(realSeconds / 60.0).toLong()
        return "${minutes / 60L}h ${minutes % 60L}m"
    }
    val seconds = if (unplayedMs == 0L) 0L else realSeconds.toLong().coerceAtLeast(1L)
    return "${seconds / 60L}m ${seconds % 60L}s"
}

/** Formats a speed as "1x", "1.25x", ... (lowercase x). */
fun formatSpeed(speed: Float): String = "${speed.toString().trimEnd('0').trimEnd('.')}x"

/** "Chapter 3 of 7" — used for parts (folder books) and embedded chapters. */
fun chapterLabel(index: Int, count: Int): String =
    "Chapter ${index + 1} of $count"

/**
 * The book's embedded chapters, flattened across parts. Only single-file books
 * carry embedded chapters today (they get exactly one part), so the flattened
 * list is that part's chapters.
 */
fun embeddedChapters(book: Audiobook): List<EmbeddedChapter> =
    book.parts.flatMap { it.chapters }

/** Global start of a part: the sum of the durations of all earlier parts. */
fun partStartMs(book: Audiobook, partIndex: Int): Long =
    book.parts.take(partIndex).sumOf { it.durationMilliseconds.coerceAtLeast(0) }

/** Index of the chapter containing [positionMs] (an offset within the part), clamped to the last chapter. */
fun chapterIndexAt(chapters: List<EmbeddedChapter>, positionMs: Long): Int {
    if (chapters.isEmpty()) return 0
    val index = chapters.indexOfFirst { positionMs < it.endMs }
    return if (index >= 0) index else chapters.lastIndex
}

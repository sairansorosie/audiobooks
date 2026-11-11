package com.lightphone.audiobooks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChapterRemainingTimeTest {
    private val minute = 60_000L
    private val hour = 60L * minute

    @Test fun `shows chapter remaining time in hours and minutes`() {
        assertEquals("4h 5m left", formatChapterTimeRemaining(5 * hour, 55 * minute, 1f))
        assertEquals("3h 0m left", formatChapterTimeRemaining(5 * hour, 2 * hour, 1f))
        assertEquals("12m left", formatChapterTimeRemaining(30 * minute, 18 * minute, 1f))
    }

    @Test fun `speed changes same chapter estimate without changing position`() {
        val chapterDuration = 80 * minute
        val chapterPosition = 20 * minute
        assertEquals("1h 0m left", formatChapterTimeRemaining(chapterDuration, chapterPosition, 1f))
        assertEquals("40m left", formatChapterTimeRemaining(chapterDuration, chapterPosition, 1.5f))
        assertEquals("30m left", formatChapterTimeRemaining(chapterDuration, chapterPosition, 2f))
        assertEquals("2h 0m left", formatChapterTimeRemaining(chapterDuration, chapterPosition, 0.5f))
    }

    @Test fun `only uses current chapter values not total audiobook`() {
        // A ten-hour book may have a 50-minute active chapter, 20 minutes into it.
        // The existing PlayerContent must pass chapter values: 30 minutes at 1x.
        assertEquals("30m left", formatChapterTimeRemaining(50 * minute, 20 * minute, 1f))
    }

    @Test fun `recalculates from seeks and chapter changes`() {
        assertEquals("15m left", formatChapterTimeRemaining(40 * minute, 25 * minute, 1f))
        assertEquals("40m left", formatChapterTimeRemaining(40 * minute, 0L, 1f))
        assertEquals("7m left", formatChapterTimeRemaining(20 * minute, 13 * minute, 1f))
    }

    @Test fun `rounds small remaining fractions upward and reaches zero at end`() {
        assertEquals("2m left", formatChapterTimeRemaining(61_000L, 0L, 1f))
        assertEquals("1m left", formatChapterTimeRemaining(minute, 59_999L, 1f))
        assertEquals("0m left", formatChapterTimeRemaining(minute, minute, 1f))
        assertEquals("0m left", formatChapterTimeRemaining(minute, minute + 5_000L, 1f))
        assertEquals("1m left", formatChapterTimeRemaining(minute, -100L, 1f))
    }

    @Test fun `hides estimate if chapter duration or speed is invalid`() {
        assertNull(formatChapterTimeRemaining(0L, 0L, 1f))
        assertNull(formatChapterTimeRemaining(-1L, 0L, 1f))
        assertNull(formatChapterTimeRemaining(hour, 0L, 0f))
        assertNull(formatChapterTimeRemaining(hour, 0L, Float.NaN))
        assertNull(formatChapterTimeRemaining(hour, 0L, Float.POSITIVE_INFINITY))
        assertNull(formatChapterTimeRemaining(hour, 0L, -1f))
    }
}

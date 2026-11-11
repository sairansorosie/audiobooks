package com.lightphone.audiobooks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemainingListeningTimeTest {
    private val minute = 60_000L
    private val hour = 60 * minute

    @Test fun overOneHourShowsHoursAndMinutes() {
        assertEquals("4h 5m", formatRemainingListeningTime(5 * hour, 55 * minute, 1f))
        assertEquals("1h 0m", formatRemainingListeningTime(hour, 0L, 1f))
        assertEquals("2h 30m", formatRemainingListeningTime(5 * hour, 0L, 2f))
    }

    @Test fun underOneHourShowsSeconds() {
        assertEquals("12m 5s", formatRemainingListeningTime(20 * minute, 7 * minute + 55_000L, 1f))
        assertEquals("20m 0s", formatRemainingListeningTime(50 * minute, 20 * minute, 1.5f))
        assertEquals("0m 1s", formatRemainingListeningTime(1L, 0L, 1f))
        assertEquals("0m 0s", formatRemainingListeningTime(minute, minute, 1f))
        assertEquals("59m 58s", formatRemainingListeningTime(hour, 1_001L, 1f))
        assertEquals("59m 59s", formatRemainingListeningTime(hour, 1L, 1f))
    }

    @Test fun sameChapterAndBookUseDifferentSourceDurations() {
        val chapterRemaining = formatRemainingListeningTime(50 * minute, 20 * minute, 1.5f)
        val bookRemaining = formatRemainingListeningTime(5 * hour, hour, 1.5f)
        assertEquals("20m 0s", chapterRemaining)
        assertEquals("2h 40m", bookRemaining)
    }

    @Test fun playbackSpeedAndSeekingRecalculate() {
        assertEquals("30m 0s", formatRemainingListeningTime(hour, 0L, 2f))
        assertEquals("40m 0s", formatRemainingListeningTime(hour, 0L, 1.5f))
        assertEquals("5m 0s", formatRemainingListeningTime(hour, 45 * minute, 3f))
        assertEquals("10m 0s", formatRemainingListeningTime(hour, 45 * minute, 1.5f))
    }

    @Test fun invalidMetadataDoesNotShowEstimate() {
        assertNull(formatRemainingListeningTime(0L, 0L, 1f))
        assertNull(formatRemainingListeningTime(-1L, 0L, 1f))
        assertNull(formatRemainingListeningTime(hour, 0L, 0f))
        assertNull(formatRemainingListeningTime(hour, 0L, -1f))
        assertNull(formatRemainingListeningTime(hour, 0L, Float.NaN))
        assertNull(formatRemainingListeningTime(hour, 0L, Float.POSITIVE_INFINITY))
    }

    @Test fun clampsPositionToValidDuration() {
        assertEquals("1h 0m", formatRemainingListeningTime(hour, -5L, 1f))
        assertEquals("0m 0s", formatRemainingListeningTime(hour, hour + 20_000L, 1f))
    }
}

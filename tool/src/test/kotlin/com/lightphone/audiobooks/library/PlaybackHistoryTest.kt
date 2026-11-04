package com.lightphone.audiobooks.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackHistoryTest {
    @Test fun successfulStartKeepsPositionSpeedAndCompletion() {
        val original = AudiobookProgress(positionMilliseconds = 12_345L,
            durationMilliseconds = 90_000L, playbackSpeed = 1.5f, completed = true,
            lastPlayedAtMilliseconds = 20L, title = "Test")
        val updated = original.withPlaybackStarted(200L)
        assertEquals(200L, updated.lastPlayedAtMilliseconds)
        assertEquals(original.positionMilliseconds, updated.positionMilliseconds)
        assertEquals(original.durationMilliseconds, updated.durationMilliseconds)
        assertEquals(original.playbackSpeed, updated.playbackSpeed)
        assertTrue(updated.completed)
        assertEquals(original.title, updated.title)
    }

    @Test fun repeatedStartsAlwaysMoveNewBookAhead() {
        assertEquals(101L, nextPlaybackTimestamp(100L, 100L))
        assertEquals(500L, nextPlaybackTimestamp(500L, 100L))
        assertEquals(601L, nextPlaybackTimestamp(500L, 600L))
    }

    @Test fun playbackDoesNotFinishUnfinishedBook() {
        assertFalse(AudiobookProgress().withPlaybackStarted(20L).completed)
    }
}

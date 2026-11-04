package com.lightphone.audiobooks.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookCompletionTest {
    @Test fun manualMarkRetainsPositionAndPlaybackSettings() {
        val existing = AudiobookProgress(positionMilliseconds = 1_200_000L,
            durationMilliseconds = 4_800_000L, playbackSpeed = 1.25f)
        val marked = existing.withManualCompletion(500L)
        assertTrue(marked.completed)
        assertEquals(1_200_000L, marked.positionMilliseconds)
        assertEquals(4_800_000L, marked.durationMilliseconds)
        assertEquals(1.25f, marked.playbackSpeed, 0f)
    }

    @Test fun neverStartedBookCanBeMarkedFinished() {
        val marked = AudiobookProgress().withManualCompletion(10L)
        assertTrue(marked.completed)
        assertEquals(0L, marked.positionMilliseconds)
    }

    @Test fun latePlayerSaveCannotClearManuallyFinishedStatus() {
        val marked = AudiobookProgress(positionMilliseconds = 25_000L)
            .withManualCompletion(10L)
        assertTrue(completedAfterPlayback(marked.completed, reachedEnd = false))
    }

    @Test fun automaticNearEndCompletionIsPreserved() {
        assertTrue(completedAfterPlayback(previouslyFinished = false, reachedEnd = true))
    }

    @Test fun unfinishedPlaybackRemainsUnfinished() {
        assertFalse(completedAfterPlayback(previouslyFinished = false, reachedEnd = false))
    }

    @Test fun manualCompletionIsIdempotent() {
        val once = AudiobookProgress(positionMilliseconds = 24L).withManualCompletion(50L)
        assertEquals(once, once.withManualCompletion(50L))
    }
}

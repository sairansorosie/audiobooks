package com.lightphone.audiobooks.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressResetTest {
    @Test fun clearFinishedBookPreservesEverythingExceptPositionAndCompletion() {
        val before = AudiobookProgress(positionMilliseconds = 12_000L,
            durationMilliseconds = 88_000L, playbackSpeed = 1.5f,
            completed = true, lastPlayedAtMilliseconds = 77L, title = "Title", author = "Author")
        val after = before.withClearedListeningProgress(100L)
        assertEquals(0L, after.positionMilliseconds)
        assertFalse(after.completed)
        assertEquals(88_000L, after.durationMilliseconds)
        assertEquals(1.5f, after.playbackSpeed, 0f)
        assertEquals(77L, after.lastPlayedAtMilliseconds)
        assertEquals("Title", after.title)
        assertEquals("Author", after.author)
    }
    @Test fun clearUnfinishedBookRemovesPosition() {
        val after = AudiobookProgress(positionMilliseconds = 67L)
            .withClearedListeningProgress(55L)
        assertEquals(0L, after.positionMilliseconds)
        assertFalse(after.completed)
        assertEquals(55L, after.lastUpdatedAtMilliseconds)
    }
    @Test fun clearIsIdempotentExceptUpdateTimestamp() {
        val first = AudiobookProgress(completed = true).withClearedListeningProgress(10L)
        assertEquals(first, first.withClearedListeningProgress(10L))
    }
    @Test fun staleSaveIsRejectedAfterClear() {
        assertTrue(isStaleProgressSave(expectedGeneration = 0L, actualGeneration = 1L))
        assertTrue(isStaleProgressSave(expectedGeneration = 1L, actualGeneration = 2L))
    }
    @Test fun newSaveAfterClearIsAccepted() {
        assertFalse(isStaleProgressSave(expectedGeneration = 1L, actualGeneration = 1L))
    }
    @Test fun playedAtTimeSurvivesRepeatedClears() {
        val p = AudiobookProgress(lastPlayedAtMilliseconds = 135L, completed = true)
        val after = p.withClearedListeningProgress(100L).withClearedListeningProgress(200L)
        assertEquals(135L, after.lastPlayedAtMilliseconds)
        assertFalse(after.completed)
    }
}

package com.lightphone.audiobooks.screens

import com.lightphone.audiobooks.library.Audiobook
import com.lightphone.audiobooks.library.AudiobookSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibraryRowStatusTest {
    private fun book(position: Long, duration: Long, completed: Boolean = false): Audiobook =
        Audiobook(
            id = "fixture",
            source = AudiobookSource.Local,
            title = "Test Book",
            playbackReference = "/test/book.mp3",
            positionMilliseconds = position,
            durationMilliseconds = duration,
            completed = completed,
        )

    @Test
    fun `zero and sub one percent are hidden`() {
        assertNull(libraryProgressPercent(book(0L, 100_000L)))
        assertNull(libraryProgressPercent(book(800L, 100_000L)))
    }

    @Test
    fun `whole book progress is floored`() {
        assertEquals(1, libraryProgressPercent(book(1_700L, 100_000L)))
        assertEquals(24, libraryProgressPercent(book(24_900L, 100_000L)))
    }

    @Test
    fun `unfinished progress is limited to ninety nine percent`() {
        assertEquals(99, libraryProgressPercent(book(99_900L, 100_000L)))
        assertEquals(99, libraryProgressPercent(book(100_000L, 100_000L)))
    }

    @Test
    fun `completed and unknown duration do not produce a percent`() {
        assertNull(libraryProgressPercent(book(100_000L, 100_000L, completed = true)))
        assertNull(libraryProgressPercent(book(10L, 0L)))
    }
}

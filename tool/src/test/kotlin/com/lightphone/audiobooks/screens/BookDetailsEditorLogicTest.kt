package com.lightphone.audiobooks.screens

import com.lightphone.audiobooks.library.Audiobook
import com.lightphone.audiobooks.library.AudiobookSource
import com.lightphone.audiobooks.library.BookMetadataOverrides
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookDetailsEditorLogicTest {
    private fun book(overrides: BookMetadataOverrides = BookMetadataOverrides()): Audiobook = Audiobook(
        id = "fixture",
        source = AudiobookSource.Local,
        title = overrides.title ?: "Imported Title",
        author = overrides.author ?: "Imported Author",
        playbackReference = "/fixture/book.mp3",
        importedTitle = "Imported Title",
        importedAuthor = "Imported Author",
        seriesName = overrides.seriesName,
        seriesPosition = overrides.seriesPosition,
        metadataOverrides = overrides,
    )

    @Test fun `empty title fails validation`() {
        assertEquals("Book title is required.", validateBookDetails(currentBookDetails(book()).copy(title = "   ")))
    }

    @Test fun `too long fields fail validation`() {
        val input = currentBookDetails(book())
        assertEquals("Book title must be 255 characters or fewer.", validateBookDetails(input.copy(title = "a".repeat(256))))
        assertEquals("Author must be 255 characters or fewer.", validateBookDetails(input.copy(author = "a".repeat(256))))
        assertEquals("Series name must be 255 characters or fewer.", validateBookDetails(input.copy(seriesName = "a".repeat(256))))
    }

    @Test fun `series position requires name and valid decimal`() {
        val input = currentBookDetails(book())
        assertEquals("Enter a series name before its position.", validateBookDetails(input.copy(seriesPosition = "2")))
        val withSeries = input.copy(seriesName = "Series")
        for (bad in listOf("-1", "two", "2,5", "1..5")) {
            assertEquals("Series position must be a nonnegative decimal, such as 2.5.", validateBookDetails(withSeries.copy(seriesPosition = bad)))
        }
        for (valid in listOf("0", "1", "2.5")) assertNull(validateBookDetails(withSeries.copy(seriesPosition = valid)))
    }

    @Test fun `blank author is an explicit override when imported author exists`() {
        val imported = book()
        val result = overridesForDraft(imported, currentBookDetails(imported), currentBookDetails(imported).copy(author = ""), false)
        assertEquals("", result.author)
    }

    @Test fun `unchanged existing overrides are retained`() {
        val saved = BookMetadataOverrides(title = "Custom", author = "")
        val current = book(saved)
        val initial = currentBookDetails(current)
        val result = overridesForDraft(current, initial, initial.copy(seriesName = "Stormlight", seriesPosition = "2.50"), false)
        assertEquals("Custom", result.title)
        assertEquals("", result.author)
        assertEquals("Stormlight", result.seriesName)
        assertEquals("2.5", result.seriesPosition)
    }

    @Test fun `restore imported clears all existing overrides`() {
        val current = book(BookMetadataOverrides(title = "Custom", author = "", seriesName = "Stormlight", seriesPosition = "3"))
        val result = overridesForDraft(current, currentBookDetails(current), importedBookDetails(current), true)
        assertEquals(BookMetadataOverrides(), result)
    }

    @Test fun `two books with same title retain their own identities`() {
        val first = book()
        val second = first.copy(id = "other")
        val firstEdited = first.copy(title = "Same")
        val secondEdited = second.copy(title = "Same")
        assertEquals("fixture", firstEdited.id)
        assertEquals("other", secondEdited.id)
    }

    @Test fun `series label is absent unless assigned`() {
        assertNull(librarySeriesLabel(book()))
        assertEquals("Stormlight · #2.5", librarySeriesLabel(book(BookMetadataOverrides(seriesName = "Stormlight", seriesPosition = "2.5"))))
    }
}

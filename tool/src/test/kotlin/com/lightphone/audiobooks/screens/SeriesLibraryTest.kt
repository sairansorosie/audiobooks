package com.lightphone.audiobooks.screens

import com.lightphone.audiobooks.library.Audiobook
import com.lightphone.audiobooks.library.AudiobookPart
import com.lightphone.audiobooks.library.AudiobookSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeriesLibraryTest {
    private fun book(
        title: String,
        series: String? = null,
        position: String? = null,
        id: String = title,
        parts: List<AudiobookPart> = emptyList(),
    ) = Audiobook(
        id = id,
        source = AudiobookSource.Local,
        title = title,
        playbackReference = "/audiobooks/$id.mp3",
        seriesName = series,
        seriesPosition = position,
        parts = parts,
    )

    @Test fun `zero series means only standalone books`() {
        val result = seriesLibraryFor(listOf(book("B"), book("A")))
        assertTrue(result.groups.isEmpty())
        assertEquals(listOf("A", "B"), result.standaloneBooks.map { it.title })
        assertEquals("0 SERIES", seriesHeaderCount(result.groups.size))
    }

    @Test fun `first series creates a group without swallowing standalones`() {
        val result = seriesLibraryFor(listOf(book("Standalone"), book("Volume", "Saga", "1")))
        assertEquals(1, result.groups.size)
        assertEquals("1 SERIES", seriesHeaderCount(result.groups.size))
        assertEquals("1 book", seriesGroupBookCount(result.groups.single().books.size))
        assertEquals(listOf("Standalone"), result.standaloneBooks.map { it.title })
    }

    @Test fun `normalizes case and whitespace without guessing series names`() {
        val result = seriesLibraryFor(listOf(
            book("B", "  STORMLIGHT  "), book("A", "Stormlight"),
            book("C", "Storm light"), book("D", "  "),
        ))
        assertEquals(2, result.groups.size)
        assertEquals(setOf("Stormlight", "Storm light"), result.groups.map { it.name }.toSet())
        assertEquals(2, result.groups.single { it.name == "Stormlight" }.books.size)
        assertEquals(listOf("D"), result.standaloneBooks.map { it.title })
    }

    @Test fun `numeric series order including fractions then unnumbered`() {
        val result = seriesLibraryFor(listOf(
            book("Three", "Saga", "3"), book("Two Half", "Saga", "2.5"),
            book("Two", "Saga", "2"), book("One", "Saga", "1"),
            book("Unnumbered B", "Saga"), book("Unnumbered A", "Saga"),
        ))
        assertEquals(listOf("One", "Two", "Two Half", "Three", "Unnumbered A", "Unnumbered B"),
            result.groups.single().books.map { it.title })
        assertEquals("#2.5", seriesVolumeLabel(book("P", "Saga", "2.5")))
        assertEquals("#2", seriesVolumeLabel(book("P", "Saga", "2.00")))
        assertNull(seriesVolumeLabel(book("P", "Saga")))
    }

    @Test fun `invalid positions are treated as missing and sorted by title`() {
        val result = seriesLibraryFor(listOf(
            book("Z", "Saga", "two"), book("A", "Saga", "-1"),
            book("C", "Saga", "10"),
        ))
        assertEquals(listOf("C", "A", "Z"), result.groups.single().books.map { it.title })
        assertNull(seriesVolumeLabel(book("X", "Saga", "2,5")))
    }

    @Test fun `group order is alphabetical and standalone books stay separate`() {
        val result = seriesLibraryFor(listOf(
            book("Zed", "Zeta"), book("A", "alpha"),
            book("The Bell Jar"), book("Apple"),
        ))
        assertEquals(listOf("alpha", "Zeta"), result.groups.map { it.name })
        assertEquals(listOf("Apple", "The Bell Jar"), result.standaloneBooks.map { it.title })
        assertEquals("2 SERIES", seriesHeaderCount(result.groups.size))
        assertEquals("2 books", seriesGroupBookCount(result.standaloneBooks.size))
    }

    @Test fun `volume ties sort by title and identity`() {
        val result = seriesLibraryFor(listOf(
            book("B", "Saga", "2"), book("A", "Saga", "2", "a"),
            book("A", "Saga", "2", "z"),
        ))
        assertEquals(listOf("a", "z", "B"), result.groups.single().books.map { it.id })
    }

    @Test fun `folder audiobook counts as one series book`() {
        val parts = listOf(AudiobookPart("/a.mp3", 5000), AudiobookPart("/b.mp3", 5000))
        val result = seriesLibraryFor(listOf(book("Folder", "Saga", parts = parts)))
        assertEquals("1 book", seriesGroupBookCount(result.groups.single().books.size))
    }

    @Test fun `removing the last assignment removes the Series view`() {
        val assigned = listOf(book("A", "Saga"))
        assertEquals(1, seriesLibraryFor(assigned).groups.size)
        assertTrue(seriesLibraryFor(listOf(assigned.single().copy(seriesName = null))).groups.isEmpty())
    }
}

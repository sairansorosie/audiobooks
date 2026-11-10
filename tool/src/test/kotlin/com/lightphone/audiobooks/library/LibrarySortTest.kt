package com.lightphone.audiobooks.library

import kotlin.test.Test
import kotlin.test.assertEquals

class LibrarySortTest {
    private fun book(id: String, title: String, author: String = "") = Audiobook(
        id = id,
        source = AudiobookSource.Local,
        title = title,
        author = author,
        playbackReference = "/$id.mp3",
    )

    @Test fun `title sort includes leading articles and ignores case`() {
        val books = listOf(book("3", "The Bell Jar"), book("2", "zoo"), book("1", "a tale"))
        assertEquals(listOf("1", "3", "2"), sortedAllBooks(books, LibrarySort.TitleAz).map { it.id })
    }

    @Test fun `author sort uses complete displayed author names`() {
        val books = listOf(book("2", "A", "Zadie Smith"), book("1", "B", "Brandon Sanderson"))
        assertEquals(listOf("1", "2"), sortedAllBooks(books, LibrarySort.AuthorAz).map { it.id })
    }

    @Test fun `same author sorts by title`() {
        val books = listOf(book("2", "Words of Radiance", "Brandon Sanderson"),
            book("1", "The Way of Kings", "brandon sanderson"))
        assertEquals(listOf("1", "2"), sortedAllBooks(books, LibrarySort.AuthorAz).map { it.id })
    }

    @Test fun `missing authors always sort last, by title`() {
        val books = listOf(book("4", "Zephyr"), book("3", "Alpha", " "),
            book("2", "Beta", "Zadie Smith"), book("1", "Gamma", "Brandon Sanderson"))
        assertEquals(listOf("1", "2", "3", "4"), sortedAllBooks(books, LibrarySort.AuthorAz).map { it.id })
    }

    @Test fun `case insensitive ties are deterministic by identity`() {
        val books = listOf(book("z", "SAME"), book("a", "same"))
        assertEquals(listOf("a", "z"), sortedAllBooks(books, LibrarySort.TitleAz).map { it.id })
    }

    @Test fun `sorting uses updated effective titles and authors`() {
        val a = book("a", "Old", "Zadie Smith").copy(title = "The New Title", author = "Alice")
        val b = book("b", "Another", "Brandon")
        assertEquals(listOf("b", "a"), sortedAllBooks(listOf(a, b), LibrarySort.TitleAz).map { it.id })
        assertEquals(listOf("a", "b"), sortedAllBooks(listOf(a, b), LibrarySort.AuthorAz).map { it.id })
    }

    @Test fun `sort labels cover five approved choices`() {
        assertEquals("Title A-Z", librarySortLabel(LibrarySort.TitleAz))
        assertEquals("Author A-Z", librarySortLabel(LibrarySort.AuthorAz))
        assertEquals("Recently Added", librarySortLabel(LibrarySort.RecentlyAdded))
        assertEquals("Recently Played", librarySortLabel(LibrarySort.RecentlyPlayed))
        assertEquals("Series A-Z", librarySortLabel(LibrarySort.SeriesAz))
        assertEquals(5, LibrarySort.entries.size)
    }

    @Test fun `recently played sorts latest resume first and never played alphabetically`() {
        val a = book("1", "Alpha").copy(lastPlayedAtMilliseconds = 100L)
        val b = book("2", "Beta").copy(lastPlayedAtMilliseconds = 200L)
        val c = book("8", "Zebra").copy(lastPlayedAtMilliseconds = 300L)
        val unplayedB = book("u2", "Unplayed Z")
        val unplayedA = book("u1", "Unplayed A")
        assertEquals(listOf("8", "2", "1", "u1", "u2"),
            sortedAllBooks(listOf(a, b, c, unplayedB, unplayedA),
                LibrarySort.RecentlyPlayed).map { it.id })
        val replayed = a.copy(lastPlayedAtMilliseconds = 400L)
        assertEquals(listOf("1", "8", "2"),
            sortedAllBooks(listOf(b, c, replayed), LibrarySort.RecentlyPlayed).map { it.id })
    }

    @Test fun `recently added sorts new scans first without file timestamps`() {
        val books = listOf(book("old", "Zebra"), book("recent", "A book"))
        val history = mapOf("Local:old" to 1000L, "Local:recent" to 2000L)
        assertEquals(listOf("recent", "old"),
            sortedAllBooks(books, LibrarySort.RecentlyAdded, history).map { it.id })
    }

    @Test fun `same-scan timestamps break ties by title then identity`() {
        val books = listOf(book("z", "Same"), book("a", "same"), book("other", "Before"))
        val history = books.associate { "Local:${it.id}" to 1234L }
        assertEquals(listOf("other", "a", "z"),
            sortedAllBooks(books, LibrarySort.RecentlyAdded, history).map { it.id })
    }

    @Test fun `books absent from history sort after detected books`() {
        val books = listOf(book("undetected", "A book"), book("known", "Z book"))
        assertEquals(listOf("known", "undetected"),
            sortedAllBooks(books, LibrarySort.RecentlyAdded, mapOf("Local:known" to 111L)).map { it.id })
    }

    @Test fun `series sort groups case and whitespace, numeric volumes, then standalones`() {
        val books = listOf(
            book("u", "The Unnumbered").copy(seriesName = "Saga"),
            book("s2", "Standalone Z"),
            book("z10", "Volume Ten").copy(seriesName = "zeta", seriesPosition = "10"),
            book("a2", "Second").copy(seriesName = " SAGA ", seriesPosition = "2"),
            book("z2", "Volume Two").copy(seriesName = "ZETA", seriesPosition = "2.5"),
            book("a1", "First").copy(seriesName = "saga", seriesPosition = "1"),
            book("s1", "Standalone A"),
        )
        assertEquals(listOf("a1", "a2", "u", "z2", "z10", "s1", "s2"),
            sortedAllBooks(books, LibrarySort.SeriesAz).map { it.id })
    }

    @Test fun `series sort treats missing or invalid positions as unnumbered`() {
        val books = listOf(
            book("missing", "B").copy(seriesName = "Saga"),
            book("invalid", "A").copy(seriesName = "Saga", seriesPosition = "-1"),
            book("ten", "Ten").copy(seriesName = "Saga", seriesPosition = "10"),
            book("two", "Two").copy(seriesName = "Saga", seriesPosition = "2"),
            book("decimal", "Decimal").copy(seriesName = "Saga", seriesPosition = "2.5"),
            book("blank", "Standalone").copy(seriesName = "  "),
        )
        assertEquals(listOf("two", "decimal", "ten", "invalid", "missing", "blank"),
            sortedAllBooks(books, LibrarySort.SeriesAz).map { it.id })
    }

    @Test fun `series sort has stable identity ties and handles no series`() {
        val a = book("a", "Same").copy(seriesName = "Saga", seriesPosition = "2.0")
        val z = book("z", "same").copy(seriesName = " saga ", seriesPosition = "2")
        assertEquals(listOf("a", "z"), sortedAllBooks(listOf(z, a), LibrarySort.SeriesAz).map { it.id })
        val standalone = listOf(book("B", "Zeta"), book("A", "Alpha"))
        assertEquals(sortedAllBooks(standalone, LibrarySort.TitleAz),
            sortedAllBooks(standalone, LibrarySort.SeriesAz))
    }

    @Test fun `series sorting uses edited effective metadata`() {
        val a = book("a", "Alpha").copy(seriesName = "Zulu", seriesPosition = "1")
        val b = book("b", "Beta").copy(seriesName = "Alpha", seriesPosition = "1")
        assertEquals(listOf("b", "a"), sortedAllBooks(listOf(a, b), LibrarySort.SeriesAz).map { it.id })
        assertEquals(listOf("a", "b"),
            sortedAllBooks(listOf(a.copy(seriesName = "Aardvark"), b), LibrarySort.SeriesAz).map { it.id })
    }
}

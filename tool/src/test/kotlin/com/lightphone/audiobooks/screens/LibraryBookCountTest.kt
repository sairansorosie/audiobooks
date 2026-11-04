package com.lightphone.audiobooks.screens

import com.lightphone.audiobooks.library.Audiobook
import com.lightphone.audiobooks.library.AudiobookPart
import com.lightphone.audiobooks.library.AudiobookSource
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryBookCountTest {
    private fun book(id: String, parts: List<AudiobookPart> = emptyList()) = Audiobook(
        id = id,
        source = AudiobookSource.Local,
        title = id,
        playbackReference = "/fixture/$id.mp3",
        parts = parts,
    )

    @Test
    fun `zero books shows plural count`() {
        assertEquals("0 BOOKS", libraryBookCountLabel(emptyList()))
    }

    @Test
    fun `one book shows singular count`() {
        assertEquals("1 BOOK", libraryBookCountLabel(listOf(book("one"))))
    }

    @Test
    fun `multiple books show plural count`() {
        assertEquals("2 BOOKS", libraryBookCountLabel(listOf(book("one"), book("two"))))
    }

    @Test
    fun `multi file audiobook counts once`() {
        val parts = listOf(
            AudiobookPart("/fixture/part1.mp3", 20_000L),
            AudiobookPart("/fixture/part2.mp3", 20_000L),
            AudiobookPart("/fixture/part3.mp3", 20_000L),
        )
        assertEquals("1 BOOK", libraryBookCountLabel(listOf(book("folder", parts))))
    }
}

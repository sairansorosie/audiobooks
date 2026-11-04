package com.lightphone.audiobooks.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.io.IOException
import java.nio.file.Files

class BookMetadataTest {
    private fun fixture(id: String = "file:123", title: String = "Original") = Audiobook(
        id = id,
        source = AudiobookSource.Local,
        title = title,
        author = "Original Author",
        playbackReference = "/fixture/audio.mp3",
        positionMilliseconds = 10_000L,
        durationMilliseconds = 50_000L,
        playbackSpeed = 1.25f,
    )

    @Test fun `absent override uses imported metadata`() {
        val book = fixture().withEffectiveMetadata(BookMetadataOverrides())
        assertEquals("Original", book.title)
        assertEquals("Original Author", book.author)
        assertNull(book.seriesName)
    }

    @Test fun `blank optional override differs from absent override`() {
        val imported = fixture().withEffectiveMetadata(BookMetadataOverrides())
        val cleared = fixture().withEffectiveMetadata(BookMetadataOverrides(author = "", seriesName = ""))
        assertEquals("Original Author", imported.author)
        assertEquals("", cleared.author)
        assertEquals("", cleared.metadataOverrides.seriesName)
        assertNull(cleared.seriesPosition)
    }

    @Test fun `effective metadata leaves playback state and identity unchanged`() {
        val before = fixture()
        val after = before.withEffectiveMetadata(BookMetadataOverrides(title = "Edited", seriesName = "Stormlight", seriesPosition = "2.5"))
        assertEquals("Edited", after.title)
        assertEquals("Original", after.importedTitle)
        assertEquals("Stormlight", after.seriesName)
        assertEquals("2.5", after.seriesPosition)
        assertEquals(before.id, after.id)
        assertEquals(before.positionMilliseconds, after.positionMilliseconds)
        assertEquals(before.playbackSpeed, after.playbackSpeed)
        assertEquals(before.playbackReference, after.playbackReference)
    }

    @Test fun `metadata persists after reload and duplicate titles remain separate`() {
        val root = Files.createTempDirectory("book-metadata-test").toFile()
        try {
            BookMetadataStore.init(root)
            BookMetadataStore.save(AudiobookSource.Local, "id1", BookMetadataOverrides(title = "Same", author = ""))
            BookMetadataStore.save(AudiobookSource.Local, "id2", BookMetadataOverrides(title = "Same", author = "Second"))
            BookMetadataStore.init(Files.createTempDirectory("book-metadata-away").toFile())
            BookMetadataStore.init(root)
            assertEquals("", BookMetadataStore.read(AudiobookSource.Local, "id1").author)
            assertEquals("Second", BookMetadataStore.read(AudiobookSource.Local, "id2").author)
            assertEquals("Same", BookMetadataStore.read(AudiobookSource.Local, "id1").title)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun `metadata save does not alter legacy playback progress`() {
        val root = Files.createTempDirectory("book-metadata-progress").toFile()
        try {
            val existing = "{\"Local:file:123\":{\"position\":10000,\"speed\":1.25,\"completed\":false}}"
            val progress = root.resolve("progress.json")
            progress.writeText(existing)
            BookMetadataStore.init(root)
            BookMetadataStore.save(AudiobookSource.Local, "file:123", BookMetadataOverrides(title = "Revised"))
            assertEquals(existing, progress.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun `corrupt metadata cannot be silently overwritten`() {
        val root = Files.createTempDirectory("book-metadata-corrupt").toFile()
        try {
            BookMetadataStore.init(root)
            BookMetadataStore.save(AudiobookSource.Local, "id", BookMetadataOverrides(title = "Good"))
            val metadata = root.resolve("book_metadata.properties")
            val raw = metadata.readText().replace("Good", "Altered")
            metadata.writeText(raw)
            BookMetadataStore.init(Files.createTempDirectory("book-metadata-away2").toFile())
            BookMetadataStore.init(root)
            assertFailsWith<IOException> { BookMetadataStore.read(AudiobookSource.Local, "id") }
            assertFailsWith<IOException> { BookMetadataStore.save(AudiobookSource.Local, "id", BookMetadataOverrides(title = "Danger")) }
            assertEquals(raw, metadata.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun `missing metadata file defaults to imported tags`() {
        val root = Files.createTempDirectory("book-metadata-missing").toFile()
        try {
            BookMetadataStore.init(root)
            assertEquals(BookMetadataOverrides(), BookMetadataStore.read(AudiobookSource.Local, "unseen"))
            assertTrue(root.listFiles().isNullOrEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun `invalid custom title is rejected without writing`() {
        val root = Files.createTempDirectory("book-metadata-title").toFile()
        try {
            BookMetadataStore.init(root)
            assertFailsWith<IllegalArgumentException> {
                BookMetadataStore.save(AudiobookSource.Local, "id", BookMetadataOverrides(title = "   "))
            }
            assertTrue(root.listFiles().isNullOrEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}

package com.lightphone.audiobooks.library

import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LibraryViewPreferenceStoreTest {
    @Test fun `defaults to All Books and survives reinitialization`() {
        val directory = Files.createTempDirectory("audiobooks-view-").toFile()
        try {
            LibraryViewPreferenceStore.init(directory)
            assertEquals(LibraryView.AllBooks, LibraryViewPreferenceStore.read())
            LibraryViewPreferenceStore.save(LibraryView.Series)
            LibraryViewPreferenceStore.init(directory)
            assertEquals(LibraryView.Series, LibraryViewPreferenceStore.read())
            LibraryViewPreferenceStore.save(LibraryView.AllBooks)
            assertEquals(LibraryView.AllBooks, LibraryViewPreferenceStore.read())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun `corrupt preferences are never silently overwritten`() {
        val directory = Files.createTempDirectory("audiobooks-view-corrupt-").toFile()
        try {
            LibraryViewPreferenceStore.init(directory)
            val path = directory.resolve("library_view.txt")
            path.writeText("unexpected")
            assertFailsWith<IOException> { LibraryViewPreferenceStore.read() }
            assertFailsWith<IOException> { LibraryViewPreferenceStore.save(LibraryView.Series) }
            assertEquals("unexpected", path.readText())
        } finally {
            directory.deleteRecursively()
        }
    }
}

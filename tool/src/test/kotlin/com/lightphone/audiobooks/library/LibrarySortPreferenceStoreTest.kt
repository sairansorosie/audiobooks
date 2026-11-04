package com.lightphone.audiobooks.library

import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LibrarySortPreferenceStoreTest {
    @Test fun `defaults to title and preserves selection across initialization`() {
        val directory = Files.createTempDirectory("audiobooks-sort-").toFile()
        try {
            LibrarySortPreferenceStore.init(directory)
            assertEquals(LibrarySort.TitleAz, LibrarySortPreferenceStore.read())
            LibrarySortPreferenceStore.save(LibrarySort.AuthorAz)
            LibrarySortPreferenceStore.init(directory)
            assertEquals(LibrarySort.AuthorAz, LibrarySortPreferenceStore.read())
            LibrarySortPreferenceStore.save(LibrarySort.RecentlyAdded)
            LibrarySortPreferenceStore.init(directory)
            assertEquals(LibrarySort.RecentlyAdded, LibrarySortPreferenceStore.read())
            LibrarySortPreferenceStore.save(LibrarySort.RecentlyPlayed)
            LibrarySortPreferenceStore.init(directory)
            assertEquals(LibrarySort.RecentlyPlayed, LibrarySortPreferenceStore.read())
            LibrarySortPreferenceStore.save(LibrarySort.TitleAz)
            assertEquals(LibrarySort.TitleAz, LibrarySortPreferenceStore.read())
        } finally { directory.deleteRecursively() }
    }

    @Test fun `corrupt preference remains intact and cannot be overwritten`() {
        val directory = Files.createTempDirectory("audiobooks-sort-corrupt-").toFile()
        try {
            LibrarySortPreferenceStore.init(directory)
            val file = directory.resolve("library_sort.txt")
            file.writeText("INVALID SORT")
            assertFailsWith<IOException> { LibrarySortPreferenceStore.read() }
            assertFailsWith<IOException> { LibrarySortPreferenceStore.save(LibrarySort.TitleAz) }
            assertEquals("INVALID SORT", file.readText())
        } finally { directory.deleteRecursively() }
    }
}

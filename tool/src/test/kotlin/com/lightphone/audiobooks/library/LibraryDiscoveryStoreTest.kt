package com.lightphone.audiobooks.library

import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LibraryDiscoveryStoreTest {
    private fun book(id: String, title: String = id) = Audiobook(
        id = id, source = AudiobookSource.Local, title = title, playbackReference = "/$id.mp3",
    )

    @Test fun `first successful discovery is kept through rescan restart and edits`() {
        val dir = Files.createTempDirectory("discovery-first-").toFile()
        val alternate = Files.createTempDirectory("discovery-alternate-").toFile()
        try {
            LibraryDiscoveryStore.init(dir)
            val initial = listOf(book("one"), book("two"))
            val first = LibraryDiscoveryStore.recordNewBooks(initial, 1000L)
            assertEquals(1000L, first["Local:one"])
            assertEquals(first, LibraryDiscoveryStore.recordNewBooks(initial.reversed(), 2000L))
            LibraryDiscoveryStore.init(alternate)
            LibraryDiscoveryStore.init(dir)
            assertEquals(first, LibraryDiscoveryStore.readAll())
            assertEquals(first, LibraryDiscoveryStore.recordNewBooks(listOf(book("one", "New title"), book("two")), 3000L))
        } finally { dir.deleteRecursively(); alternate.deleteRecursively() }
    }

    @Test fun `new books rank ahead without losing older unseen records`() {
        val dir = Files.createTempDirectory("discovery-new-").toFile()
        try {
            LibraryDiscoveryStore.init(dir)
            LibraryDiscoveryStore.recordNewBooks(listOf(book("old")), 1000L)
            val next = LibraryDiscoveryStore.recordNewBooks(listOf(book("new")), 2000L)
            assertTrue(next.getValue("Local:new") > next.getValue("Local:old"))
            assertEquals(2, next.size)
            assertEquals(next, LibraryDiscoveryStore.recordNewBooks(emptyList(), 3000L))
        } finally { dir.deleteRecursively() }
    }

    @Test fun `clock rollback still places new discoveries ahead`() {
        val dir = Files.createTempDirectory("discovery-clock-").toFile()
        try {
            LibraryDiscoveryStore.init(dir)
            LibraryDiscoveryStore.recordNewBooks(listOf(book("earlier")), 5000L)
            val entries = LibraryDiscoveryStore.recordNewBooks(listOf(book("later")), 4000L)
            assertEquals(5001L, entries.getValue("Local:later"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun `corrupt data remains untouched and prevents recording new books`() {
        val dir = Files.createTempDirectory("discovery-corrupt-").toFile()
        try {
            LibraryDiscoveryStore.init(dir)
            val file = dir.resolve("library_first_seen.properties")
            file.writeText("corrupt data")
            assertFailsWith<IOException> { LibraryDiscoveryStore.readAll() }
            assertFailsWith<IOException> { LibraryDiscoveryStore.recordNewBooks(listOf(book("test")), 1000L) }
            assertEquals("corrupt data", file.readText())
        } finally { dir.deleteRecursively() }
    }
}

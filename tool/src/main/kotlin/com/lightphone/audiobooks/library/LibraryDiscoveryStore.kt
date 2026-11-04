package com.lightphone.audiobooks.library

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.util.Base64
import java.util.Properties

/** The date an audiobook identity was first found in a successful local scan.
 * Unlike file modification times, this date never changes on a routine rescan.
 * Entries are retained when a book temporarily disappears or storage is inaccessible.
 */
object LibraryDiscoveryStore {
    private const val FILE_NAME = "library_first_seen.properties"
    private const val SCHEMA = "schema"
    private const val VERSION = "1"
    private const val CHECKSUM = "checksum"
    private const val BOOK_PREFIX = "book."
    private val lock = Any()
    private var directory: File? = null
    private var cache: Properties? = null

    fun init(filesDir: File) = synchronized(lock) {
        if (directory != filesDir) cache = null
        directory = filesDir
    }

    private fun file(): File = File(
        directory ?: error("LibraryDiscoveryStore.init must be called first"), FILE_NAME,
    )

    fun qualifiedId(book: Audiobook): String = "${book.source.name}:${book.id}"

    private fun propertyKey(book: Audiobook): String = BOOK_PREFIX +
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(qualifiedId(book).toByteArray(Charsets.UTF_8))

    private fun checksum(entries: Properties): String {
        val digest = MessageDigest.getInstance("SHA-256")
        entries.stringPropertyNames().filter { it != CHECKSUM }.sorted().forEach { key ->
            val value = entries.getProperty(key)
            digest.update("${key.length}:$key${value.length}:$value\n".toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fresh() = Properties().apply {
        setProperty(SCHEMA, VERSION)
        setProperty(CHECKSUM, checksum(this))
    }

    private fun load(): Properties {
        cache?.let { return it }
        val file = file()
        if (!file.exists()) return fresh().also { cache = it }
        if (!file.isFile) throw IOException("Discovery history is not a file: $file")
        val loaded = Properties()
        try {
            file.inputStream().bufferedReader(Charsets.UTF_8).use { loaded.load(it) }
        } catch (failure: Exception) {
            throw IOException("Unable to read discovery history: $file", failure)
        }
        if (loaded.getProperty(SCHEMA) != VERSION ||
            loaded.getProperty(CHECKSUM) != checksum(loaded)
        ) throw IOException("Corrupted or unsupported discovery history: $file")
        // Validate every value before caching, not only those currently visible.
        decode(loaded)
        return loaded.also { cache = it }
    }

    private fun decode(entries: Properties): Map<String, Long> = buildMap {
        entries.stringPropertyNames().filter { it.startsWith(BOOK_PREFIX) }.forEach { key ->
            val identity = try {
                String(Base64.getUrlDecoder().decode(key.removePrefix(BOOK_PREFIX)), Charsets.UTF_8)
            } catch (failure: IllegalArgumentException) {
                throw IOException("Invalid discovery identity", failure)
            }
            if (identity.isBlank()) throw IOException("Empty discovery identity")
            val timestamp = entries.getProperty(key).toLongOrNull()
                ?: throw IOException("Invalid first-discovered time for $identity")
            if (timestamp <= 0L) throw IOException("Nonpositive first-discovered time for $identity")
            put(identity, timestamp)
        }
    }

    fun readAll(): Map<String, Long> = synchronized(lock) { decode(load()) }

    /** Called only for a successful scan with books. Existing IDs are never restamped. */
    fun recordNewBooks(books: List<Audiobook>, detectedAtMillis: Long = System.currentTimeMillis()): Map<String, Long> =
        synchronized(lock) {
            val existing = load()
            val current = decode(existing)
            val newlyFound = books.distinctBy(::qualifiedId).filter { !existing.containsKey(propertyKey(it)) }
            if (newlyFound.isEmpty()) return current

            // If the device clock moves backwards, new discoveries still sort first.
            val previousMaximum = current.values.maxOrNull() ?: 0L
            if (previousMaximum == Long.MAX_VALUE) throw IOException("Discovery timestamp exhausted")
            val stamp = maxOf(detectedAtMillis, previousMaximum + 1L, 1L)
            val updated = Properties().apply { putAll(existing) }
            newlyFound.forEach { updated.setProperty(propertyKey(it), stamp.toString()) }
            updated.setProperty(CHECKSUM, checksum(updated))
            persist(updated)
            cache = updated
            decode(updated)
        }

    private fun persist(entries: Properties) {
        val target = file()
        val parent = target.parentFile
        if (parent?.isDirectory != true) throw IOException("Discovery storage directory is missing")
        val bytes = ByteArrayOutputStream().also { out ->
            OutputStreamWriter(out, Charsets.UTF_8).use { entries.store(it, null) }
        }.toByteArray()
        val temporary = File(parent, "$FILE_NAME.tmp")
        FileOutputStream(temporary).use {
            it.write(bytes)
            it.fd.sync()
        }
        // Same-directory rename is atomic on the target Android filesystem.
        if (!temporary.renameTo(target)) throw IOException("Unable to save discovery history: $target")
    }
}

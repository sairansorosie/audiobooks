package com.lightphone.audiobooks.library

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.util.Base64
import java.util.Properties

/**
 * Private metadata overrides, independent of the existing progress.json.
 *
 * Uses the JDK/Android Properties format and a file-level checksum so malformed
 * or truncated state is never mistaken for a fresh empty library. A failed
 * load prevents all writes; a failed write leaves the last good file in place.
 * Every operation is serialized on the same monitor.
 */
object BookMetadataStore {
    private const val FILE_NAME = "book_metadata.properties"
    private const val SCHEMA_KEY = "schema"
    private const val SCHEMA_VERSION = "1"
    private const val CHECKSUM_KEY = "checksum"
    private val lock = Any()
    private var filesDir: File? = null
    private var cache: Properties? = null

    fun init(directory: File) = synchronized(lock) {
        if (filesDir != directory) cache = null
        filesDir = directory
    }

    private fun file(): File = File(
        filesDir ?: error("BookMetadataStore.init must run before reading metadata"),
        FILE_NAME,
    )

    private fun prefix(source: AudiobookSource, id: String): String {
        val identity = "${source.name}:$id"
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(identity.toByteArray(Charsets.UTF_8))
        return "book.$encoded."
    }

    private fun checksum(data: Properties): String {
        val digest = MessageDigest.getInstance("SHA-256")
        data.stringPropertyNames().filter { it != CHECKSUM_KEY }.sorted().forEach { key ->
            val value = data.getProperty(key)
            // Length prefixes avoid ambiguity for keys/values containing newlines.
            digest.update("${key.length}:$key${value.length}:$value\n".toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fresh(): Properties = Properties().apply {
        setProperty(SCHEMA_KEY, SCHEMA_VERSION)
        setProperty(CHECKSUM_KEY, checksum(this))
    }

    private fun load(): Properties {
        cache?.let { return it }
        val source = file()
        if (!source.exists()) return fresh().also { cache = it }
        if (!source.isFile) throw IOException("Metadata store path is not a file: $source")
        val loaded = Properties()
        try {
            source.inputStream().bufferedReader(Charsets.UTF_8).use { loaded.load(it) }
        } catch (failure: Exception) {
            throw IOException("Unable to parse audiobook metadata: $source", failure)
        }
        if (loaded.getProperty(SCHEMA_KEY) != SCHEMA_VERSION ||
            loaded.getProperty(CHECKSUM_KEY) != checksum(loaded)
        ) {
            throw IOException("Audiobook metadata is corrupt or has an unsupported version: $source")
        }
        return loaded.also { cache = it }
    }

    fun read(source: AudiobookSource, id: String): BookMetadataOverrides = synchronized(lock) {
        val entries = load()
        val prefix = prefix(source, id)
        BookMetadataOverrides(
            title = entries.getProperty(prefix + "title"),
            author = entries.getProperty(prefix + "author"),
            seriesName = entries.getProperty(prefix + "seriesName"),
            seriesPosition = entries.getProperty(prefix + "seriesPosition"),
        )
    }

    /** Throws if a write failed. Caller must not display "Saved" on failure. */
    fun save(source: AudiobookSource, id: String, overrides: BookMetadataOverrides) = synchronized(lock) {
        require(overrides.title == null ||
            (overrides.title.isNotBlank() && overrides.title.length <= 255)) {
            "A custom title must contain 1 to 255 nonblank characters"
        }
        require(overrides.author == null || overrides.author.length <= 255) {
            "Author must be 255 characters or fewer"
        }
        require(overrides.seriesName == null || overrides.seriesName.length <= 255) {
            "Series name must be 255 characters or fewer"
        }
        require(overrides.seriesPosition == null || overrides.seriesPosition.isEmpty() ||
            overrides.seriesPosition.matches(Regex("(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?"))) {
            "Series position must be a nonnegative decimal"
        }

        val updated = Properties().apply { putAll(load()) }
        val keyPrefix = prefix(source, id)
        val fields = mapOf(
            "title" to overrides.title,
            "author" to overrides.author,
            "seriesName" to overrides.seriesName,
            "seriesPosition" to overrides.seriesPosition,
        )
        fields.forEach { (name, value) ->
            if (value == null) updated.remove(keyPrefix + name)
            else updated.setProperty(keyPrefix + name, value)
        }
        updated.setProperty(CHECKSUM_KEY, checksum(updated))
        persist(updated)
        cache = updated
    }

    private fun persist(entries: Properties) {
        val target = file()
        val parent = target.parentFile
        if (!parent.isDirectory) throw IOException("Metadata directory missing: $parent")
        val output = ByteArrayOutputStream()
        OutputStreamWriter(output, Charsets.UTF_8).use { entries.store(it, null) }
        val temporary = File(parent, "$FILE_NAME.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(output.toByteArray())
            stream.fd.sync()
        }
        // On the Android/Linux filesystem rename replaces the target atomically.
        // Do not delete the previous version as a fallback if rename fails.
        if (!temporary.renameTo(target)) {
            throw IOException("Unable to replace audiobook metadata: $target")
        }
    }
}

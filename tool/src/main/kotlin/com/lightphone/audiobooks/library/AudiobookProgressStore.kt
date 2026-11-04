package com.lightphone.audiobooks.library

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val FILE_NAME = "progress.json"

data class AudiobookProgress(
    val positionMilliseconds: Long = 0,
    val durationMilliseconds: Long = 0,
    val playbackSpeed: Float = 1f,
    val completed: Boolean = false,
    val lastPlayedAtMilliseconds: Long = 0,
    val lastUpdatedAtMilliseconds: Long = 0,
    val playbackReference: String = "",
    val title: String = "",
    val author: String = "",
)

/** Keep the independent finished flag sticky on ordinary playback saves. */
internal fun completedAfterPlayback(previouslyFinished: Boolean, reachedEnd: Boolean): Boolean =
    previouslyFinished || reachedEnd

/** Manual completion changes only completed/time, never the listening position. */
internal fun AudiobookProgress.withManualCompletion(updatedAt: Long): AudiobookProgress =
    copy(completed = true, lastUpdatedAtMilliseconds = updatedAt)

/** Reset only listening state, not metadata, history, duration or playback speed. */
internal fun AudiobookProgress.withClearedListeningProgress(nowMillis: Long): AudiobookProgress =
    copy(positionMilliseconds = 0L, completed = false, lastUpdatedAtMilliseconds = nowMillis)

/** Reject writes scheduled against a pre-clear snapshot. */
internal fun isStaleProgressSave(expectedGeneration: Long, actualGeneration: Long): Boolean =
    expectedGeneration != actualGeneration

/** Timestamp must advance even when two play events occur in the same millisecond. */
internal fun nextPlaybackTimestamp(now: Long, previousMax: Long): Long =
    if (previousMax >= now && previousMax < Long.MAX_VALUE) previousMax + 1L
    else maxOf(now, previousMax)

/** A playback start does not mark Finished or alter listening position. */
internal fun AudiobookProgress.withPlaybackStarted(timestamp: Long): AudiobookProgress =
    copy(lastPlayedAtMilliseconds = timestamp)

/** Durable, source-independent progress and ordering metadata. */
object AudiobookProgressStore {
    private lateinit var filesDir: File
    private var cache: MutableMap<String, AudiobookProgress>? = null
    private val progressGenerations = mutableMapOf<String, Long>()

    fun init(filesDir: File) {
        this.filesDir = filesDir
    }

    private fun qualifiedId(source: AudiobookSource, id: String): String = "${source.name}:$id"

    private fun storeFile() = File(filesDir, FILE_NAME)

    /** Loads the store on first use (in-memory cache afterwards). */
    private fun load(): MutableMap<String, AudiobookProgress> = synchronized(this) {
        cache?.let { return it }
        val loaded = buildMap {
            readJson(storeFile())?.let { root ->
                root.keys().forEach { key ->
                    val o = root.getJSONObject(key)
                    put(
                        key,
                        AudiobookProgress(
                            positionMilliseconds = o.optLong("position"),
                            durationMilliseconds = o.optLong("duration"),
                            playbackSpeed = o.optDouble("speed", 1.0).toFloat(),
                            completed = o.optBoolean("completed"),
                            lastPlayedAtMilliseconds = o.optLong("lastPlayed"),
                            lastUpdatedAtMilliseconds = o.optLong("updated"),
                            playbackReference = o.optString("reference"),
                            title = o.optString("title"),
                            author = o.optString("author"),
                        ),
                    )
                }
            }
        }.toMutableMap()
        cache = loaded
        loaded
    }

    private fun persist(entries: Map<String, AudiobookProgress>) = synchronized(this) {
        val root = JSONObject()
        entries.forEach { (key, progress) ->
            root.put(
                key,
                JSONObject()
                    .put("position", progress.positionMilliseconds)
                    .put("duration", progress.durationMilliseconds)
                    .put("speed", progress.playbackSpeed.toDouble())
                    .put("completed", progress.completed)
                    .put("lastPlayed", progress.lastPlayedAtMilliseconds)
                    .put("updated", progress.lastUpdatedAtMilliseconds)
                    .put("reference", progress.playbackReference)
                    .put("title", progress.title)
                    .put("author", progress.author),
            )
        }
        writeJsonAtomically(storeFile(), root)
    }

    @Synchronized
    fun read(source: AudiobookSource, id: String): AudiobookProgress =
        load()[qualifiedId(source, id)] ?: AudiobookProgress()

    /**
     * Permanently mark a book as finished without moving its saved position.
     * This uses the existing progress.json schema (no destructive migration).
     * A failed/corrupt read or disk write raises rather than claiming success.
     */
    @Synchronized
    fun markFinished(book: Audiobook) {
        val file = storeFile()
        if (file.exists() && readJson(file) == null) {
            throw IOException("Unreadable progress store; refusing to overwrite it")
        }
        val current = load()
        val key = qualifiedId(book.source, book.id)
        val existing = current[key] ?: AudiobookProgress()
        if (existing.completed) return
        val next = current.toMutableMap()
        next[key] = existing.withManualCompletion(System.currentTimeMillis())

        // Assemble the exact legacy JSON schema, retaining every book's
        // position, duration, speed, timestamps, and metadata references.
        val root = JSONObject()
        next.forEach { (id, progress) ->
            root.put(id, JSONObject()
                .put("position", progress.positionMilliseconds)
                .put("duration", progress.durationMilliseconds)
                .put("speed", progress.playbackSpeed.toDouble())
                .put("completed", progress.completed)
                .put("lastPlayed", progress.lastPlayedAtMilliseconds)
                .put("updated", progress.lastUpdatedAtMilliseconds)
                .put("reference", progress.playbackReference)
                .put("title", progress.title)
                .put("author", progress.author))
        }
        val temp = File(file.parentFile, "${file.name}.mark-${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temp).use { output ->
                output.write(root.toString().toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            try {
                Files.move(temp.toPath(), file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            cache = next
        } finally {
            temp.delete()
        }
    }

    @Synchronized
    fun progressGeneration(source: AudiobookSource, id: String): Long =
        progressGenerations[qualifiedId(source, id)] ?: 0L

    /**
     * Clear listening state while preserving book metadata, speed and last-played
     * history. Increment the generation only after the file was safely replaced.
     */
    @Synchronized
    fun clearListeningProgress(book: Audiobook) {
        val file = storeFile()
        if (file.exists() && readJson(file) == null) {
            throw IOException("Unreadable progress store; refusing to overwrite it")
        }
        val current = load()
        val key = qualifiedId(book.source, book.id)
        val previous = current[key] ?: AudiobookProgress()
        val next = current.toMutableMap()
        next[key] = previous.withClearedListeningProgress(System.currentTimeMillis())
        val generation = progressGenerations[key] ?: 0L
        val nextGeneration = Math.addExact(generation, 1L)
        val root = JSONObject()
        next.forEach { (id, entry) ->
            root.put(id, JSONObject()
                .put("position", entry.positionMilliseconds)
                .put("duration", entry.durationMilliseconds)
                .put("speed", entry.playbackSpeed.toDouble())
                .put("completed", entry.completed)
                .put("lastPlayed", entry.lastPlayedAtMilliseconds)
                .put("updated", entry.lastUpdatedAtMilliseconds)
                .put("reference", entry.playbackReference)
                .put("title", entry.title)
                .put("author", entry.author))
        }
        val temp = File(file.parentFile, "${file.name}.clear-${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temp).use { out ->
                out.write(root.toString().toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            try {
                Files.move(temp.toPath(), file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            cache = next
            progressGenerations[key] = nextGeneration
        } finally {
            temp.delete()
        }
    }

    /** Record an observed successful play/resume; a preview never calls this method. */
    @Synchronized
    fun recordPlaybackStarted(book: Audiobook) {
        val file = storeFile()
        if (file.exists() && readJson(file) == null) {
            throw IOException("Unreadable progress store; refusing to overwrite playback history")
        }
        val current = load()
        val key = qualifiedId(book.source, book.id)
        val previousMax = current.values.maxOfOrNull { it.lastPlayedAtMilliseconds } ?: 0L
        val timestamp = nextPlaybackTimestamp(System.currentTimeMillis(), previousMax)
        val previous = current[key] ?: AudiobookProgress()
        val next = current.toMutableMap()
        next[key] = previous.withPlaybackStarted(timestamp)
        persist(next)
        cache = next
    }

    /** Forgets all stored progress/metadata for a book. */
    @Synchronized
    fun clear(source: AudiobookSource, id: String) {
        val entries = load()
        if (entries.remove(qualifiedId(source, id)) != null) persist(entries)
    }

    @Synchronized
    fun saveLocal(
        book: Audiobook,
        positionMilliseconds: Long,
        durationMilliseconds: Long,
        playbackSpeed: Float,
        expectedGeneration: Long = progressGeneration(book.source, book.id),
    ) {
        if (isStaleProgressSave(expectedGeneration,
                progressGeneration(book.source, book.id))) return
        val entries = load()
        val existing = entries[qualifiedId(book.source, book.id)] ?: AudiobookProgress()
        val duration = durationMilliseconds.coerceAtLeast(0)
        var position = positionMilliseconds.coerceAtLeast(0)
        val reachedEnd = duration > 0 && duration - position.coerceAtMost(duration) <= 30_000L
        val completed = completedAfterPlayback(existing.completed, reachedEnd)
        // Automatic near-end completion snaps to the duration, while a
        // manually finished book keeps the actual listening position.
        if (reachedEnd) position = duration
        entries[qualifiedId(book.source, book.id)] = existing.copy(
            positionMilliseconds = position,
            durationMilliseconds = duration.takeIf { it > 0 } ?: existing.durationMilliseconds,
            playbackSpeed = playbackSpeed.takeIf { it > 0 } ?: existing.playbackSpeed,
            completed = completed,
            lastUpdatedAtMilliseconds = System.currentTimeMillis(),
            playbackReference = book.playbackReference,
            title = book.title,
            author = book.author,
        )
        persist(entries)
    }
}

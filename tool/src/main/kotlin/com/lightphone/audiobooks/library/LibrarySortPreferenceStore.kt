package com.lightphone.audiobooks.library

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Local-only sort preference. Independent from book metadata and playback state. */
object LibrarySortPreferenceStore {
    private const val FILE_NAME = "library_sort.txt"
    private val lock = Any()
    private var directory: File? = null

    fun init(filesDir: File) = synchronized(lock) {
        directory = filesDir
    }

    private fun file(): File = File(
        directory ?: error("LibrarySortPreferenceStore.init must run first"),
        FILE_NAME,
    )

    fun read(): LibrarySort = synchronized(lock) {
        val path = file()
        if (!path.exists()) return LibrarySort.TitleAz
        if (!path.isFile) throw IOException("Library sort preference is not a file: $path")
        when (path.readText(Charsets.UTF_8).trim()) {
            LibrarySort.TitleAz.name -> LibrarySort.TitleAz
            LibrarySort.AuthorAz.name -> LibrarySort.AuthorAz
            LibrarySort.SeriesAz.name -> LibrarySort.SeriesAz
            LibrarySort.RecentlyAdded.name -> LibrarySort.RecentlyAdded
            LibrarySort.RecentlyPlayed.name -> LibrarySort.RecentlyPlayed
            else -> throw IOException("Unrecognized Library sort preference: $path")
        }
    }

    /** Keep a corrupt preference intact rather than silently replacing it. */
    fun save(sort: LibrarySort) = synchronized(lock) {
        read()
        val target = file()
        if (target.parentFile?.isDirectory != true) throw IOException("Library storage directory missing")
        val temporary = File(target.parentFile, "$FILE_NAME.tmp")
        FileOutputStream(temporary).use { output ->
            output.write((sort.name + "\n").toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (!temporary.renameTo(target)) {
            throw IOException("Unable to save Library sort preference: $target")
        }
    }
}

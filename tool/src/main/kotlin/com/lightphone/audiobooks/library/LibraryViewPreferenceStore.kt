package com.lightphone.audiobooks.library

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

enum class LibraryView { AllBooks, Series }

/** Minimal, local preference for the Library tab; independent of book metadata. */
internal object LibraryViewPreferenceStore {
    private const val FILE_NAME = "library_view.txt"
    private val lock = Any()
    private var directory: File? = null

    fun init(filesDir: File) = synchronized(lock) {
        directory = filesDir
    }

    private fun file(): File = File(
        directory ?: error("LibraryViewPreferenceStore.init must run first"),
        FILE_NAME,
    )

    fun read(): LibraryView = synchronized(lock) {
        val path = file()
        if (!path.exists()) return LibraryView.AllBooks
        if (!path.isFile) throw IOException("Library view preference is not a file: $path")
        when (path.readText(Charsets.UTF_8).trim()) {
            LibraryView.AllBooks.name -> LibraryView.AllBooks
            LibraryView.Series.name -> LibraryView.Series
            else -> throw IOException("Unrecognized Library view preference: $path")
        }
    }

    /** Refuses to replace an unreadable value with a deceptively valid default. */
    fun save(view: LibraryView) = synchronized(lock) {
        read()
        val target = file()
        if (target.parentFile?.isDirectory != true) throw IOException("Library storage directory missing")
        val temporary = File(target.parentFile, "$FILE_NAME.tmp")
        FileOutputStream(temporary).use { output ->
            output.write((view.name + "\n").toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (!temporary.renameTo(target)) {
            throw IOException("Unable to save Library view preference: $target")
        }
    }
}

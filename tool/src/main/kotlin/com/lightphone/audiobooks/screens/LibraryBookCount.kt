package com.lightphone.audiobooks.screens

import com.lightphone.audiobooks.library.Audiobook

/** Counts audiobook records, not the individual files inside a folder book. */
internal fun libraryBookCountLabel(books: List<Audiobook>): String =
    "${books.size} ${if (books.size == 1) "BOOK" else "BOOKS"}"

package com.lightphone.audiobooks.library

import java.util.Locale

/** All Books choices, including first-detected Recently Added order. */
enum class LibrarySort { TitleAz, AuthorAz, RecentlyAdded, RecentlyPlayed }

fun librarySortLabel(sort: LibrarySort): String = when (sort) {
    LibrarySort.TitleAz -> "Title A-Z"
    LibrarySort.AuthorAz -> "Author A-Z"
    LibrarySort.RecentlyAdded -> "Recently Added"
    LibrarySort.RecentlyPlayed -> "Recently Played"
}

private fun compareTitles(a: Audiobook, b: Audiobook): Int =
    a.title.lowercase(Locale.ROOT).compareTo(b.title.lowercase(Locale.ROOT))

private fun compareIdentity(a: Audiobook, b: Audiobook): Int {
    val source = a.source.name.compareTo(b.source.name)
    return if (source != 0) source else a.id.compareTo(b.id)
}

/** Sort effective, displayed metadata without changing book identities or source data. */
fun sortedAllBooks(
    books: List<Audiobook>,
    sort: LibrarySort,
    firstDetected: Map<String, Long> = emptyMap(),
): List<Audiobook> =
    books.sortedWith(Comparator { a, b ->
        val comparison = when (sort) {
            LibrarySort.TitleAz -> compareTitles(a, b)
            LibrarySort.RecentlyPlayed -> {
                val byPlayback = b.lastPlayedAtMilliseconds.compareTo(a.lastPlayedAtMilliseconds)
                if (byPlayback != 0) byPlayback else compareTitles(a, b)
            }
            LibrarySort.RecentlyAdded -> {
                val aTime = firstDetected[LibraryDiscoveryStore.qualifiedId(a)] ?: Long.MIN_VALUE
                val bTime = firstDetected[LibraryDiscoveryStore.qualifiedId(b)] ?: Long.MIN_VALUE
                val byTime = bTime.compareTo(aTime)
                if (byTime != 0) byTime else compareTitles(a, b)
            }
            LibrarySort.AuthorAz -> {
                val aMissing = a.author.isBlank()
                val bMissing = b.author.isBlank()
                when {
                    aMissing && !bMissing -> 1
                    !aMissing && bMissing -> -1
                    aMissing -> compareTitles(a, b)
                    else -> {
                        val author = a.author.lowercase(Locale.ROOT)
                            .compareTo(b.author.lowercase(Locale.ROOT))
                        if (author != 0) author else compareTitles(a, b)
                    }
                }
            }
        }
        if (comparison != 0) comparison else compareIdentity(a, b)
    })

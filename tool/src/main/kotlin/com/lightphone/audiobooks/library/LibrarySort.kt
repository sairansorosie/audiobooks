package com.lightphone.audiobooks.library

import java.util.Locale
import java.math.BigDecimal

/** All Books choices, including first-detected Recently Added order. */
enum class LibrarySort { TitleAz, AuthorAz, SeriesAz, RecentlyAdded, RecentlyPlayed }

fun librarySortLabel(sort: LibrarySort): String = when (sort) {
    LibrarySort.TitleAz -> "Title A-Z"
    LibrarySort.AuthorAz -> "Author A-Z"
    LibrarySort.SeriesAz -> "Series A-Z"
    LibrarySort.RecentlyAdded -> "Recently Added"
    LibrarySort.RecentlyPlayed -> "Recently Played"
}

private fun compareTitles(a: Audiobook, b: Audiobook): Int =
    a.title.lowercase(Locale.ROOT).compareTo(b.title.lowercase(Locale.ROOT))

private fun compareIdentity(a: Audiobook, b: Audiobook): Int {
    val source = a.source.name.compareTo(b.source.name)
    return if (source != 0) source else a.id.compareTo(b.id)
}

/** The Series view's proven decimal policy, now used only for ordering the flat Library. */
private val validSeriesPosition = Regex("(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?")

private fun numericSeriesPosition(value: String?): BigDecimal? =
    value?.trim()?.takeIf { validSeriesPosition.matches(it) }
        ?.let { runCatching { BigDecimal(it) }.getOrNull() }

private fun compareSeries(a: Audiobook, b: Audiobook): Int {
    val aName = a.seriesName?.trim().orEmpty().lowercase(Locale.ROOT)
    val bName = b.seriesName?.trim().orEmpty().lowercase(Locale.ROOT)
    if (aName.isEmpty() != bName.isEmpty()) return if (aName.isEmpty()) 1 else -1
    if (aName.isEmpty()) return compareTitles(a, b)
    val byName = aName.compareTo(bName)
    if (byName != 0) return byName
    val aNumber = numericSeriesPosition(a.seriesPosition)
    val bNumber = numericSeriesPosition(b.seriesPosition)
    if ((aNumber == null) != (bNumber == null)) return if (aNumber == null) 1 else -1
    if (aNumber != null && bNumber != null) {
        val byNumber = aNumber.compareTo(bNumber)
        if (byNumber != 0) return byNumber
    }
    return compareTitles(a, b)
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
            LibrarySort.SeriesAz -> compareSeries(a, b)
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

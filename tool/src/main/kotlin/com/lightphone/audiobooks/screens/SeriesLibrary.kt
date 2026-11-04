package com.lightphone.audiobooks.screens

import com.lightphone.audiobooks.library.Audiobook
import java.math.BigDecimal
import java.util.Locale

internal data class SeriesGroup(
    val name: String,
    val books: List<Audiobook>,
)

internal data class SeriesLibrary(
    val groups: List<SeriesGroup>,
    val standaloneBooks: List<Audiobook>,
)

private val titleOrder = compareBy<Audiobook> { it.title.lowercase(Locale.ROOT) }
    .thenBy { it.title }
    .thenBy { it.id }

private val validPosition = Regex("(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?")

/** An absent or invalid position stays unnumbered rather than breaking the Library. */
private fun numericPosition(value: String?): BigDecimal? =
    value?.trim()?.takeIf { validPosition.matches(it) }
        ?.let { runCatching { BigDecimal(it) }.getOrNull() }

internal fun seriesVolumeLabel(book: Audiobook): String? = numericPosition(book.seriesPosition)
    ?.stripTrailingZeros()
    ?.toPlainString()
    ?.let { "#$it" }

internal fun seriesHeaderCount(count: Int): String = "$count SERIES"

internal fun seriesGroupBookCount(count: Int): String =
    "$count ${if (count == 1) "book" else "books"}"

/**
 * Pure presentation grouping. The Audiobook id remains the book identity;
 * titles, author names, folder names and audio parts do not imply a series.
 */
internal fun seriesLibraryFor(books: List<Audiobook>): SeriesLibrary {
    val grouped = linkedMapOf<String, MutableList<Audiobook>>()
    val standalone = mutableListOf<Audiobook>()
    books.forEach { book ->
        val name = book.seriesName?.trim().orEmpty()
        if (name.isEmpty()) {
            standalone.add(book)
        } else {
            grouped.getOrPut(name.lowercase(Locale.ROOT)) { mutableListOf() }.add(book)
        }
    }

    val volumeOrder = Comparator<Audiobook> { first, second ->
        val firstPosition = numericPosition(first.seriesPosition)
        val secondPosition = numericPosition(second.seriesPosition)
        val positionOrder = when {
            firstPosition != null && secondPosition != null -> firstPosition.compareTo(secondPosition)
            firstPosition != null -> -1
            secondPosition != null -> 1
            else -> 0
        }
        if (positionOrder != 0) positionOrder else titleOrder.compare(first, second)
    }
    val seriesGroups = grouped.values.map { members ->
        // Prefer the displayed spelling from the group's first title A-Z.
        val firstByTitle = members.minWith(titleOrder)
        SeriesGroup(
            name = firstByTitle.seriesName.orEmpty().trim(),
            books = members.sortedWith(volumeOrder),
        )
    }.sortedWith(compareBy<SeriesGroup> { it.name.lowercase(Locale.ROOT) }.thenBy { it.name })

    return SeriesLibrary(seriesGroups, standalone.sortedWith(titleOrder))
}

package com.lightphone.audiobooks.screens

import com.lightphone.audiobooks.library.Audiobook
import com.lightphone.audiobooks.library.BookMetadataOverrides
import java.math.BigDecimal

/** A draft belongs to the editor, not the persisted audiobook until Save. */
internal data class BookDetailsDraft(
    val title: String,
    val author: String,
    val seriesName: String,
    val seriesPosition: String,
)

internal fun currentBookDetails(book: Audiobook): BookDetailsDraft = BookDetailsDraft(
    title = book.title,
    author = book.author,
    seriesName = book.seriesName.orEmpty(),
    seriesPosition = book.seriesPosition.orEmpty(),
)

internal fun importedBookDetails(book: Audiobook): BookDetailsDraft = BookDetailsDraft(
    title = book.importedTitle,
    author = book.importedAuthor,
    seriesName = book.importedSeriesName.orEmpty(),
    seriesPosition = book.importedSeriesPosition.orEmpty(),
)

private val seriesPositionPattern = Regex("(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?")

/** Returns the first actionable validation error; no implicit truncation. */
internal fun validateBookDetails(draft: BookDetailsDraft): String? {
    val title = draft.title.trim()
    val series = draft.seriesName.trim()
    val position = draft.seriesPosition.trim()
    return when {
        title.isEmpty() -> "Book title is required."
        title.length > 255 -> "Book title must be 255 characters or fewer."
        draft.author.trim().length > 255 -> "Author must be 255 characters or fewer."
        series.length > 255 -> "Series name must be 255 characters or fewer."
        position.isNotEmpty() && series.isEmpty() -> "Enter a series name before its position."
        position.isNotEmpty() && !seriesPositionPattern.matches(position) ->
            "Series position must be a nonnegative decimal, such as 2.5."
        else -> null
    }
}

private fun normalizeBookDetails(draft: BookDetailsDraft): BookDetailsDraft {
    val series = draft.seriesName.trim()
    val position = draft.seriesPosition.trim()
    return draft.copy(
        title = draft.title.trim(),
        author = draft.author.trim(),
        seriesName = series,
        seriesPosition = if (series.isEmpty() || position.isEmpty()) ""
            else if (seriesPositionPattern.matches(position)) BigDecimal(position).stripTrailingZeros().toPlainString()
            else position,
    )
}

/**
 * Preserve existing overrides for untouched fields. Restore Imported Details
 * explicitly clears old overrides, but only when the entire draft is saved.
 */
internal fun overridesForDraft(
    book: Audiobook,
    initial: BookDetailsDraft,
    draft: BookDetailsDraft,
    restoringImported: Boolean,
): BookMetadataOverrides {
    check(validateBookDetails(draft) == null) { "Validate the draft before saving." }
    val normalized = normalizeBookDetails(draft)
    val baseline = normalizeBookDetails(initial)
    val imported = normalizeBookDetails(importedBookDetails(book))
    val old = book.metadataOverrides

    fun select(value: String, before: String, importedValue: String, current: String?): String? = when {
        restoringImported -> if (value == importedValue) null else value
        value == before -> current
        value == importedValue -> null
        else -> value
    }
    return BookMetadataOverrides(
        title = select(normalized.title, baseline.title, imported.title, old.title),
        author = select(normalized.author, baseline.author, imported.author, old.author),
        seriesName = select(normalized.seriesName, baseline.seriesName, imported.seriesName, old.seriesName),
        seriesPosition = if (normalized.seriesName.isEmpty()) null else select(
            normalized.seriesPosition, baseline.seriesPosition, imported.seriesPosition, old.seriesPosition,
        ),
    )
}

/** A compact tertiary label, only when the book has an effective series. */
internal fun librarySeriesLabel(book: Audiobook): String? {
    val series = book.seriesName?.takeIf { it.isNotBlank() } ?: return null
    val position = book.seriesPosition?.takeIf { it.isNotBlank() }
    return if (position == null) series else "$series · #$position"
}

package com.lightphone.audiobooks.library

/**
 * Null means "use the imported value"; an empty string is an explicit user
 * override for optional fields such as author or series.
 */
data class BookMetadataOverrides(
    val title: String? = null,
    val author: String? = null,
    val seriesName: String? = null,
    val seriesPosition: String? = null,
)

/** Resolves locally edited fields without modifying audio tags or book identity. */
internal fun Audiobook.withEffectiveMetadata(overrides: BookMetadataOverrides): Audiobook {
    val effectiveSeries = overrides.seriesName ?: importedSeriesName
    return copy(
        title = overrides.title ?: importedTitle,
        author = overrides.author ?: importedAuthor,
        seriesName = effectiveSeries,
        seriesPosition = if (effectiveSeries.isNullOrBlank()) null
            else overrides.seriesPosition ?: importedSeriesPosition,
        metadataOverrides = overrides,
    )
}

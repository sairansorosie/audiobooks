package com.lightphone.audiobooks.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.lightphone.audiobooks.AppLightViewModel
import com.lightphone.audiobooks.AudiobookLibrary
import com.lightphone.audiobooks.library.Audiobook
import com.lightphone.audiobooks.library.AudiobookProgressStore
import com.lightphone.audiobooks.library.LocalBookRepository
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

internal enum class BookOptionsResult { DetailsSaved, MarkedFinished, ClearedProgress }

internal fun canClearListeningProgress(completed: Boolean, positionMs: Long): Boolean =
    completed || positionMs > 0L

internal data class BookOptionsUi(
    val title: String,
    val completed: Boolean,
    val canClearProgress: Boolean,
    val busy: Boolean = false,
    val loading: Boolean = true,
    val bookMissing: Boolean = false,
    val confirmClear: Boolean = false,
    val error: String? = null,
    val clearError: String? = null,
)

/** Shared actions; each source screen provides its own existing audio-reset path. */
internal class BookOptionsViewModel(
    private val initialBook: Audiobook,
    private val prepareClear: suspend (Audiobook) -> Unit,
) : AppLightViewModel<BookOptionsResult>() {
    val state = MutableStateFlow(BookOptionsUi(
        title = initialBook.title,
        completed = initialBook.completed,
        canClearProgress = canClearListeningProgress(
            initialBook.completed, initialBook.positionMilliseconds),
    ))
    private var resolvedBook = initialBook

    override fun onScreenShow(screen: SimpleLightScreen<BookOptionsResult>) {
        super.onScreenShow(screen)
        if (state.value.busy) return
        state.update { it.copy(loading = true) }
        viewModelScope.launch {
            runCatching { currentBook() }
                .onSuccess { book ->
                    state.update {
                        it.copy(
                            title = book.title,
                            completed = book.completed,
                            canClearProgress = canClearListeningProgress(book.completed, book.positionMilliseconds),
                            error = null,
                            bookMissing = false,
                            loading = false,
                        )
                    }
                }
                .onFailure {
                    state.update { previous -> previous.copy(
                        error = "This audiobook is no longer available. Return to Library and sync.",
                        bookMissing = true,
                        loading = false,
                    ) }
                }
        }
    }

    /** Identity is source + ID, never display title or mutable metadata. */
    private suspend fun currentBook(): Audiobook = withContext(Dispatchers.IO) {
        val book = LocalBookRepository.refreshedBooks().firstOrNull {
            it.id == initialBook.id && it.source == initialBook.source
        } ?: throw IOException("Audiobook no longer available")
        resolvedBook = book
        book
    }

    fun bookForEditor(): Audiobook = resolvedBook

    fun markFinished(onSuccess: () -> Unit) {
        if (state.value.busy || state.value.loading || state.value.bookMissing || state.value.completed) return
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = runCatching {
                val book = currentBook()
                withContext(Dispatchers.IO) { AudiobookProgressStore.markFinished(book) }
            }
            if (result.isSuccess) {
                state.update { it.copy(busy = false) }
                onSuccess()
            } else {
                state.update { it.copy(busy = false, error = "Could not mark this book finished. Please try again.") }
            }
        }
    }

    fun requestClear() {
        if (state.value.busy || state.value.loading || state.value.bookMissing || !state.value.canClearProgress) return
        state.update { it.copy(confirmClear = true, clearError = null) }
    }

    fun cancelClear() {
        if (state.value.busy) return
        state.update { it.copy(confirmClear = false, clearError = null) }
    }

    fun clearProgress(onSuccess: () -> Unit) {
        if (state.value.busy || !state.value.confirmClear) return
        state.update { it.copy(busy = true, clearError = null) }
        viewModelScope.launch {
            val result = runCatching {
                val book = currentBook()
                // Source-specific path: Player reuses its handle; Library uses
                // the existing detached-session reset helper. Never touch other audio.
                prepareClear(book)
                withContext(Dispatchers.IO) { AudiobookProgressStore.clearListeningProgress(book) }
            }
            if (result.isSuccess) {
                state.update { it.copy(busy = false) }
                onSuccess()
            } else {
                state.update { it.copy(
                    busy = false,
                    clearError = "Could not clear progress. Your audio and details were not deleted.",
                ) }
            }
        }
    }
}

internal class BookOptionsScreen(
    sealedActivity: SealedLightActivity,
    private val book: Audiobook,
    private val onPrepareClear: suspend (Audiobook) -> Unit,
) : LightScreen<BookOptionsResult, BookOptionsViewModel>(sealedActivity) {
    override val viewModelClass: Class<BookOptionsViewModel>
        get() = BookOptionsViewModel::class.java

    override fun createViewModel(): BookOptionsViewModel {
        AudiobookLibrary.init(lightContext.filesDir)
        return BookOptionsViewModel(book, onPrepareClear)
    }

    @Composable
    override fun Content() {
        val ui by viewModel.state.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()
        LightTheme(colors = themeColors) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .background(LightThemeTokens.colors.background),
                ) {
                    LightTopBar(
                        leftButton = LightBarButton.LightIcon(
                            icon = LightIcons.BACK,
                            onClick = { if (!ui.busy) goBack() },
                            contentDescription = "Back",
                        ),
                        center = LightTopBarCenter.Text("Book Options"),
                    )
                    LightText(
                        text = ui.title,
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        align = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp)
                            .semantics { contentDescription = ui.title },
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        LightScrollView {
                            BookOptionTextRow("Edit Book Details", enabled = !ui.busy && !ui.loading && !ui.bookMissing) {
                                navigateTo(screenFactory = {
                                    EditBookDetailsScreen(it, viewModel.bookForEditor())
                                }) { saved ->
                                    if (saved) goBack(BookOptionsResult.DetailsSaved)
                                }
                            }
                            if (!ui.completed) {
                                BookOptionTextRow("Mark as Finished", enabled = !ui.busy && !ui.loading && !ui.bookMissing) {
                                    viewModel.markFinished { goBack(BookOptionsResult.MarkedFinished) }
                                }
                            }
                            if (ui.canClearProgress) {
                                BookOptionTextRow("Clear Progress", enabled = !ui.busy && !ui.loading && !ui.bookMissing) {
                                    viewModel.requestClear()
                                }
                            }
                            ui.error?.let { error ->
                                LightText(
                                    text = error,
                                    variant = LightTextVariant.Detail,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                )
                            }
                        }
                    }
                }
                if (ui.confirmClear) {
                    ClearProgressConfirmation(
                        title = ui.title,
                        inFlight = ui.busy,
                        error = ui.clearError,
                        onCancel = viewModel::cancelClear,
                        onConfirm = {
                            viewModel.clearProgress { goBack(BookOptionsResult.ClearedProgress) }
                        },
                    )
                }
            }
        }
    }

    override fun goBack(result: BookOptionsResult?) {
        if (!viewModel.state.value.busy) super.goBack(result)
    }
}

@Composable
private fun BookOptionTextRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    LightText(
        text = label,
        variant = LightTextVariant.Copy,
        modifier = Modifier.fillMaxWidth()
            .lightClickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

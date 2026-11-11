package com.lightphone.audiobooks.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import com.lightphone.audiobooks.AppLightViewModel
import com.lightphone.audiobooks.AudiobookLibrary
import com.lightphone.audiobooks.library.Audiobook
import com.lightphone.audiobooks.library.BookMetadataStore
import com.lightphone.audiobooks.library.withEffectiveMetadata
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.designVerticalPxToDp
import com.thelightphone.sdk.ui.gridUnitsAsDp
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

internal data class BookEditorUi(
    val initial: BookDetailsDraft,
    val draft: BookDetailsDraft,
    val restoringImported: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val confirmDiscard: Boolean = false,
)

/** The editor never seeks, queues, pauses, or reinitializes the audio player. */
internal class EditBookDetailsViewModel(
    source: Audiobook,
) : AppLightViewModel<Boolean>() {
    private val book: Audiobook
    val state: MutableStateFlow<BookEditorUi>
    private var canLeave = false
    private val readError: String?

    init {
        var failure: String? = null
        val effective = try {
            source.withEffectiveMetadata(BookMetadataStore.read(source.source, source.id))
        } catch (e: Exception) {
            failure = "Could not read saved book details. Editing is disabled until the metadata is repaired."
            source
        }
        book = effective
        readError = failure
        val initial = currentBookDetails(effective)
        state = MutableStateFlow(BookEditorUi(initial = initial, draft = initial, error = failure))
    }

    fun set(field: String, value: String) {
        state.update { old ->
            if (old.saving || readError != null) old else old.copy(
                draft = when (field) {
                    "title" -> old.draft.copy(title = value)
                    "author" -> old.draft.copy(author = value)
                    "seriesName" -> old.draft.copy(seriesName = value)
                    "seriesPosition" -> old.draft.copy(seriesPosition = value)
                    else -> old.draft
                },
                error = null,
            )
        }
    }

    fun restoreImported() {
        if (readError != null || state.value.saving) return
        state.update { it.copy(draft = importedBookDetails(book), restoringImported = true, error = null) }
    }

    fun save(onSaved: () -> Unit) {
        val snapshot = state.value
        if (snapshot.saving || readError != null) return
        val validation = validateBookDetails(snapshot.draft)
        if (validation != null) {
            state.update { it.copy(error = validation) }
            return
        }
        val overrides = overridesForDraft(book, snapshot.initial, snapshot.draft, snapshot.restoringImported)
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { BookMetadataStore.save(book.source, book.id, overrides) }
                canLeave = true
                onSaved()
            } catch (e: Exception) {
                state.update { it.copy(saving = false, error = "Unable to save book details. Your edits are still here. ${e.message.orEmpty()}") }
            }
        }
    }

    fun discardChanges(onDiscard: () -> Unit) {
        canLeave = true
        state.update { it.copy(confirmDiscard = false) }
        onDiscard()
    }

    fun keepEditing() = state.update { it.copy(confirmDiscard = false) }

    override fun onBackPressed(): Boolean {
        val current = state.value
        if (canLeave) return false
        if (current.saving) return true
        if (current.draft != current.initial ||
            (current.restoringImported && book.metadataOverrides != com.lightphone.audiobooks.library.BookMetadataOverrides())
        ) {
            state.update { it.copy(confirmDiscard = true) }
            return true
        }
        return false
    }
}

internal class EditBookDetailsScreen(
    sealedActivity: SealedLightActivity,
    private val book: Audiobook,
) : LightScreen<Boolean, EditBookDetailsViewModel>(sealedActivity) {
    override val viewModelClass: Class<EditBookDetailsViewModel>
        get() = EditBookDetailsViewModel::class.java

    override fun createViewModel(): EditBookDetailsViewModel {
        AudiobookLibrary.init(lightContext.filesDir)
        return EditBookDetailsViewModel(book)
    }

    @Composable
    override fun Content() {
        val ui by viewModel.state.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()
        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier.fillMaxSize().background(LightThemeTokens.colors.background)
                    .padding(top = 1f.gridUnitsAsDp()),
            ) {
                LightTopBar(
                    center = LightTopBarCenter.Text("Edit Book Details"),
                )
                Box(modifier = Modifier.weight(1f)) {
                    LightScrollView {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                            editField("Book title", ui.draft.title, "title", "Enter book title")
                            editField("Author", ui.draft.author, "author", "Optional")
                            editField("Series name", ui.draft.seriesName, "seriesName", "Optional")
                            editField("Series position", ui.draft.seriesPosition, "seriesPosition", "e.g. 2.5")
                            LightText(
                                text = "Restore Imported Details",
                                variant = LightTextVariant.Detail,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .lightClickable(enabled = !ui.saving, onClick = viewModel::restoreImported)
                                    .padding(vertical = 18.dp),
                            )
                            ui.error?.let {
                                LightText(
                                    text = it,
                                    variant = LightTextVariant.Detail,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                            }
                        }
                    }
                }
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        LightBarButton.Text(text = "CANCEL", onClick = { goBack() }),
                        LightBarButton.Text(text = if (ui.saving) "SAVING" else "SAVE", onClick = {
                            viewModel.save { goBack(true) }
                        }),
                    ),
                )
            }
            if (ui.confirmDiscard) {
                Dialog(onDismissRequest = viewModel::keepEditing) {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(LightThemeTokens.colors.background).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        LightText(text = "Discard unsaved changes?", variant = LightTextVariant.Copy)
                        LightText(
                            text = "KEEP EDITING", variant = LightTextVariant.Detail,
                            modifier = Modifier.fillMaxWidth().lightClickable(onClick = viewModel::keepEditing).padding(vertical = 8.dp),
                        )
                        LightText(
                            text = "DISCARD CHANGES", variant = LightTextVariant.Detail,
                            modifier = Modifier.fillMaxWidth().lightClickable(onClick = {
                                viewModel.discardChanges { goBack() }
                            }).padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }

    /** Matches the SDK LightTextField layout, but distinguishes placeholders
     *  from actual values using the theme's secondary text color. */
    @Composable
    private fun editField(label: String, value: String, key: String, placeholder: String) {
        val isPlaceholder = value.isBlank()
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            LightText(
                text = label,
                variant = LightTextVariant.Detail,
                modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .lightClickable {
                        navigateTo(screenFactory = { BookTextInputScreen(it, label, value) }) { text ->
                            viewModel.set(key, text)
                        }
                    }
                    .padding(top = 0.25f.gridUnitsAsDp()),
            ) {
                LightText(
                    text = if (isPlaceholder) placeholder else value,
                    variant = LightTextVariant.Copy,
                    lighten = isPlaceholder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(3f.designVerticalPxToDp())
                        .background(LightThemeTokens.colors.content),
                )
            }
        }
    }
}

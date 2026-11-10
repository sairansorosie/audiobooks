package com.lightphone.audiobooks.screens

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.lightphone.audiobooks.AudiobookLibrary
import com.lightphone.audiobooks.AppLightViewModel
import com.lightphone.audiobooks.PlayerSession
import com.lightphone.audiobooks.library.Audiobook
import com.lightphone.audiobooks.library.LocalBookRepository
import com.thelightphone.sdk.audio.DefaultLightAudio
import com.lightphone.audiobooks.library.LibrarySort
import com.lightphone.audiobooks.library.LibrarySortPreferenceStore
import com.lightphone.audiobooks.library.LibraryDiscoveryStore
import com.lightphone.audiobooks.library.librarySortLabel
import com.lightphone.audiobooks.library.sortedAllBooks
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.checkPermission
import com.thelightphone.sdk.rememberPermissionRequestLauncher
import com.thelightphone.sdk.shared.LightServiceMethod
import com.thelightphone.sdk.shared.getOrNull
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.lightClickable
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel : AppLightViewModel<Unit>() {

    val books = MutableStateFlow<List<Audiobook>>(emptyList())
    val loading = MutableStateFlow(true)
    val selectedSort = MutableStateFlow(LibrarySort.TitleAz)
    val sortError = MutableStateFlow<String?>(null)
    val firstDetected = MutableStateFlow<Map<String, Long>>(emptyMap())
    val discoveryError = MutableStateFlow<String?>(null)
    private var sortLoaded = false
    private val sortWriteMutex = Mutex()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        refresh()
    }

    private suspend fun loadSortPreference() {
        if (sortLoaded) return
        withContext(Dispatchers.IO) { runCatching { LibrarySortPreferenceStore.read() } }
            .onSuccess { selectedSort.value = it }
            .onFailure { sortError.value = "Saved All Books sort could not be read." }
        sortLoaded = true
    }

    fun selectSort(sort: LibrarySort) {
        if (sort == selectedSort.value) return
        selectedSort.value = sort
        // Use the latest value if options change again before the IO write.
        viewModelScope.launch {
            sortWriteMutex.withLock {
                withContext(Dispatchers.IO) {
                    runCatching { LibrarySortPreferenceStore.save(selectedSort.value) }
                }.onSuccess { sortError.value = null }
                    .onFailure { sortError.value = "Unable to remember the All Books sort." }
            }
        }
    }

    private suspend fun publishBooks(snapshot: List<Audiobook>) {
        loadSortPreference()
        val discoveryResult = withContext(Dispatchers.IO) {
            runCatching { LibraryDiscoveryStore.readAll() }
        }
        firstDetected.value = discoveryResult.getOrDefault(emptyMap())
        discoveryError.value = LocalBookRepository.discoveryError.value ?: when {
            discoveryResult.isFailure -> "Recently Added history is unreadable; it has not been overwritten."
            else -> null
        }
        books.value = snapshot
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            // The first Library show kicks the existing initial scan.
            if (!LocalBookRepository.hasScanned) LocalBookRepository.scan()
            publishBooks(LocalBookRepository.refreshedBooks())
            loading.value = false
        }
    }

    /** Explicit scan after permissions change; existing file transfer is preserved. */
    fun scan() {
        viewModelScope.launch {
            loading.value = true
            LocalBookRepository.scan()
            publishBooks(LocalBookRepository.refreshedBooks())
            loading.value = false
        }
    }
}

// Polls after launching the READ_MEDIA_AUDIO request until the grant lands
// (or times out), then rescans so the library appears without a manual scan.
// The window is human-scale: the system dialog can sit open while the user
// reads it, so 60 s beats the 5 s that let the first version expire early.
private const val PERMISSION_POLLS = 120
private const val PERMISSION_POLL_DELAY_MS = 500L

@InitialScreen
class LibraryScreen(private val audioActivity: SealedLightActivity) :
    LightScreen<Unit, LibraryViewModel>(audioActivity) {

    // LightActivity retains the screen object on its back stack but disposes
    // its Compose content when navigating. Remembering these in Content() loses
    // both offsets; instance-owned states restore them on Player/editor return.
    private val allBooksScroll = ScrollState(0)

    override val viewModelClass: Class<LibraryViewModel>
        get() = LibraryViewModel::class.java

    override fun createViewModel(): LibraryViewModel {
        AudiobookLibrary.init(lightContext.filesDir)
        return LibraryViewModel()
    }

    @Composable
    override fun Content() {
        val books by viewModel.books.collectAsState()
        var sortAnchor by remember { mutableStateOf(IntRect.Zero) }
        val markScope = rememberCoroutineScope()
        val loading by viewModel.loading.collectAsState()
        val selectedSort by viewModel.selectedSort.collectAsState()
        val sortError by viewModel.sortError.collectAsState()
        val firstDetected by viewModel.firstDetected.collectAsState()
        val discoveryError by viewModel.discoveryError.collectAsState()
        var sortMenuOpen by remember { mutableStateOf(false) }
        val sortedBooks = remember(books, selectedSort, firstDetected) {
            sortedAllBooks(books, selectedSort, firstDetected)
        }
        val themeColors by LightThemeController.colors.collectAsState()
        // The detached player keeps playing after the Player screen pops;
        // PlayerSession records what it's doing. Resolved against the current
        // book list (a deleted book hides the button).
        val playingBook = if (PlayerSession.isPlaying) {
            books.firstOrNull { it.id == PlayerSession.loadedBookId }
        } else {
            null
        }

        // The audio permission (READ_MEDIA_AUDIO) is declared in
        // lighttool.toml; the SDK permission flow asks for it. When the
        // library is empty: if the permission is missing, launch the SDK
        // permission flow (system dialog) and rescan once the grant lands; if
        // it's present, rescan once — the initial scan ran before a fresh
        // grant (e.g. granted in system settings) and left a stale empty
        // snapshot. Guarded so it runs once per screen instance: no dialog
        // spam on every navigation, no scan loop on loading flips.
        val permissionLauncher = rememberPermissionRequestLauncher(Manifest.permission.READ_MEDIA_AUDIO)
        var permissionPrompted = remember { false }
        LaunchedEffect(books.isEmpty(), loading) {
            if (books.isEmpty() && !loading && !permissionPrompted) {
                permissionPrompted = true
                val granted = checkPermission(Manifest.permission.READ_MEDIA_AUDIO).getOrNull()
                    ?.permissionResult == LightServiceMethod.GetPermission.Result.Granted
                if (granted) {
                    viewModel.scan()
                } else {
                    permissionLauncher?.launch()
                    // The dialog can sit open for a while — poll past the
                    // human-scale window (60 s), then rescan when it lands.
                    repeat(PERMISSION_POLLS) {
                        delay(PERMISSION_POLL_DELAY_MS)
                        if (checkPermission(Manifest.permission.READ_MEDIA_AUDIO).getOrNull()
                                ?.permissionResult == LightServiceMethod.GetPermission.Result.Granted
                        ) {
                            viewModel.scan()
                            return@LaunchedEffect
                        }
                    }
                }
            }
        }

        LightTheme(colors = themeColors) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LightThemeTokens.colors.background),
                ) {
                    // The Library has no top bar; reserve the same height as
                    // the side (scrollbar) buffer so the top gap matches the
                    // gutters around the list.
                    Spacer(modifier = Modifier.height(2f.gridUnitsAsDp()))
                    LightText(
                        text = "Audiobooks",
                        variant = LightTextVariant.Heading,
                        modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
                    )
                    sortError?.let { message ->
                        LightText(
                            text = message,
                            variant = LightTextVariant.Detail,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                    }
                    discoveryError?.let { message ->
                        LightText(
                            text = message,
                            variant = LightTextVariant.Detail,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        LightScrollView(scrollState = allBooksScroll) {
                            if (!loading || books.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(start = 24.dp, end = 24.dp, bottom = 0.dp)
                                        .heightIn(min = 48.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LightText(
                                        text = libraryBookCountLabel(books),
                                        variant = LightTextVariant.Detail,
                                        modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                                    )
                                    Row(
                                        modifier = Modifier
                                            .onGloballyPositioned { sortAnchor = popupAnchorBounds(it) }
                                            .lightClickable(onClickLabel = "Sort books") { sortMenuOpen = true }
                                            .heightIn(min = 48.dp)
                                            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        LightText(
                                            text = librarySortLabel(selectedSort),
                                            variant = LightTextVariant.Paragraph,
                                        )
                                        LightIcon(
                                            icon = LightIcons.DOWN,
                                            modifier = Modifier.offset(y = (-3).dp),
                                            size = 1.0f,
                                            contentDescription = null,
                                        )
                                    }
                                }
                            }
                            when {
                                loading && books.isEmpty() -> StatusText("Scanning your library…")
                                books.isEmpty() -> StatusText(
                                    "No books found. Copy audiobooks into the Audiobooks folder on your device.",
                                )
                                else -> sortedBooks.forEachIndexed { index, book ->
                                    BookRow(
                                        book = book,
                                        onOpen = { openPlayer(book) },
                                        onMenu = { _ -> openBookOptions(book) },
                                        showDivider = index < sortedBooks.lastIndex,
                                    )
                                }
                            }
                        }
                    }
                    LightBottomBar(
                        modifier = Modifier.navigationBarsPadding(),
                        items = listOf(
                            LightBarButton.LightIcon(
                                icon = LightIcons.SETTINGS,
                                onClick = { openSettings() },
                                contentDescription = "Settings",
                            ),
                            playingBook?.let { book ->
                                LightBarButton.Text(
                                    text = "NOW PLAYING",
                                    onClick = { openPlayer(book) },
                                )
                            },
                            // Balancing spacer: keeps NOW PLAYING centered
                            // (the bar's mixed icon/text/empty layout).
                            null,
                        ),
                    )
                }
            }
            if (sortMenuOpen) {
                LibrarySortMenu(
                    anchorBounds = sortAnchor,
                    selected = selectedSort,
                    onDismiss = { sortMenuOpen = false },
                    onSelect = { selection ->
                        sortMenuOpen = false
                        if (selection != selectedSort) {
                            viewModel.selectSort(selection)
                            // A deliberate re-sort starts at the first book, not
                            // the old offset.
                            markScope.launch { allBooksScroll.scrollTo(0) }
                        }
                    },
                )
            }
        }
    }

    private fun openPlayer(book: Audiobook) {
        navigateTo(screenFactory = { PlayerScreen(it, book) })
    }

    private fun openBookOptions(book: Audiobook) {
        navigateTo(screenFactory = {
            BookOptionsScreen(it, book, onPrepareClear = { current ->
                pauseAndRewindFromLibrary(DefaultLightAudio(audioActivity), current)
            })
        }) { viewModel.refresh() }
    }

    private fun openSettings() {
        navigateTo(screenFactory = { SettingsScreen(it) })
    }
}

/** Five persistent sorts, anchored to the existing text selector. */
@Composable
private fun LibrarySortMenu(
    anchorBounds: IntRect,
    selected: LibrarySort,
    onDismiss: () -> Unit,
    onSelect: (LibrarySort) -> Unit,
) {
    Popup(
        popupPositionProvider = LightAnchoredMenuPositionProvider(anchorBounds),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier.widthIn(min = 175.dp, max = 230.dp)
                .heightIn(max = anchoredMenuMaxHeight(anchorBounds))
                .border(1.dp, LightThemeTokens.colors.contentSecondary.copy(alpha = 0.28f))
                .background(LightThemeTokens.colors.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            listOf(LibrarySort.TitleAz, LibrarySort.AuthorAz, LibrarySort.SeriesAz,
                LibrarySort.RecentlyAdded, LibrarySort.RecentlyPlayed)
                .forEach { option ->
                    LightText(
                        text = librarySortLabel(option),
                        variant = LightTextVariant.Copy,
                        underline = selected == option,
                        modifier = Modifier.fillMaxWidth()
                            .lightClickable(onClick = { onSelect(option) })
                            .padding(vertical = 12.dp),
                    )
                }
        }
    }
}

@Composable
private fun BookRow(
    book: Audiobook,
    onOpen: () -> Unit,
    onMenu: (IntRect) -> Unit,
    showSeriesLabel: Boolean = true,
    seriesNumber: String? = null,
    showDivider: Boolean = true,
) {
    var rowMenuAnchor by remember { mutableStateOf(IntRect.Zero) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .lightClickable(onClick = onOpen)
                .padding(start = 24.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Keep volume number and first title line in one top-aligned group;
            // the right-hand progress/menu stack still centers against its height.
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top,
            ) {
                seriesNumber?.let { number ->
                    LightText(
                        text = number,
                        variant = LightTextVariant.Detail,
                        modifier = Modifier.widthIn(min = 28.dp, max = 44.dp)
                            .padding(top = 2.dp, end = 4.dp),
                        maxLines = 1,
                    )
                }
                // Keep the wider title/author column approved in Phase 04.
                Column(modifier = Modifier.weight(1f)) {
                    LightText(
                        text = book.title,
                        variant = LightTextVariant.Subheading,
                    )
                    if (book.author.isNotBlank()) {
                        LightText(
                            text = book.author,
                            variant = LightTextVariant.Paragraph,
                            lighten = true,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (showSeriesLabel) {
                        librarySeriesLabel(book)?.let { series ->
                            LightText(
                                text = series,
                                variant = LightTextVariant.Detail,
                                lighten = true,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.width(44.dp),
                horizontalAlignment = Alignment.End,
            ) {
                // Reserve a status row only when there is something to show.
                // Otherwise the three-dot icon sits at the vertical center of
                // the entire title/author text block, including wrapped titles.
                val percent = if (book.completed) null else libraryProgressPercent(book)
                if (book.completed || percent != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(22.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        if (book.completed) {
                            FinishedCheckmark(
                                withCircle = false,
                                modifier = Modifier.size(20.dp)
                                    .semantics { contentDescription = "Finished" },
                            )
                        } else if (percent != null) {
                            LightText(
                                text = "$percent%",
                                variant = LightTextVariant.Fine,
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 44.dp)
                        .lightClickable(onClickLabel = "More options for ${book.title}") {
                            onMenu(rowMenuAnchor)
                        },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    LightIcon(
                        icon = LightIcons.ELLIPSES,
                        modifier = Modifier.rotate(90f)
                            .onGloballyPositioned { rowMenuAnchor = popupAnchorBounds(it) },
                        size = 1.5f,
                        contentDescription = "More options for ${book.title}",
                    )
                }
            }
        }
        if (showDivider) LibraryRowDivider()
    }
}

/** Subtle divider used only between adjacent book rows. */
@Composable
private fun LibraryRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp)
            .height(1.dp)
            .background(LightThemeTokens.colors.contentSecondary.copy(alpha = 0.2f)),
    )
}

@Composable
private fun StatusText(text: String) {
    LightText(
        text = text,
        variant = LightTextVariant.Copy,
        modifier = Modifier.padding(24.dp),
    )
}

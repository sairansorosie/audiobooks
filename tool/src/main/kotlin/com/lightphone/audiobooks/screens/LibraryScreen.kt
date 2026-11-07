package com.lightphone.audiobooks.screens

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.vector.PathParser
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
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.lightphone.audiobooks.AudiobookLibrary
import com.lightphone.audiobooks.AppLightViewModel
import com.lightphone.audiobooks.PlayerSession
import com.lightphone.audiobooks.library.Audiobook
import com.lightphone.audiobooks.library.LocalBookRepository
import com.lightphone.audiobooks.library.AudiobookProgressStore
import com.thelightphone.sdk.audio.DefaultLightAudio
import com.lightphone.audiobooks.library.LibraryView
import com.lightphone.audiobooks.library.LibraryViewPreferenceStore
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
    val selectedView = MutableStateFlow(LibraryView.AllBooks)
    val viewError = MutableStateFlow<String?>(null)
    val selectedSort = MutableStateFlow(LibrarySort.TitleAz)
    val sortError = MutableStateFlow<String?>(null)
    val firstDetected = MutableStateFlow<Map<String, Long>>(emptyMap())
    val discoveryError = MutableStateFlow<String?>(null)
    private var sortLoaded = false
    private val sortWriteMutex = Mutex()
    private var preferenceLoaded = false
    private val viewWriteMutex = Mutex()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        refresh()
    }

    private suspend fun loadViewPreference() {
        if (preferenceLoaded) return
        withContext(Dispatchers.IO) { runCatching { LibraryViewPreferenceStore.read() } }
            .onSuccess { selectedView.value = it }
            .onFailure { viewError.value = "Saved Library view could not be read." }
        preferenceLoaded = true
    }

    /** Every disk write uses the latest selection, even if tabs change quickly. */
    private suspend fun persistView() {
        viewWriteMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching { LibraryViewPreferenceStore.save(selectedView.value) }
            }.onSuccess { viewError.value = null }
                .onFailure { viewError.value = "Unable to remember the Library view." }
        }
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
        loadViewPreference()
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
        // Removing the final series must also clear a previously saved Series tab.
        if (seriesLibraryFor(snapshot).groups.isEmpty() && selectedView.value == LibraryView.Series) {
            selectedView.value = LibraryView.AllBooks
            persistView()
        }
    }

    fun selectView(view: LibraryView) {
        if (view == selectedView.value) return
        if (view == LibraryView.Series && seriesLibraryFor(books.value).groups.isEmpty()) return
        selectedView.value = view
        viewModelScope.launch { persistView() }
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
    private val seriesScroll = ScrollState(0)

    override val viewModelClass: Class<LibraryViewModel>
        get() = LibraryViewModel::class.java

    override fun createViewModel(): LibraryViewModel {
        AudiobookLibrary.init(lightContext.filesDir)
        return LibraryViewModel()
    }

    @Composable
    override fun Content() {
        val books by viewModel.books.collectAsState()
        var menuBook by remember { mutableStateOf<Audiobook?>(null) }
        var menuAnchor by remember { mutableStateOf(IntRect.Zero) }
        var sortAnchor by remember { mutableStateOf(IntRect.Zero) }
        var markError by remember { mutableStateOf<String?>(null) }
        var markInFlight by remember { mutableStateOf(false) }
        var clearBook by remember { mutableStateOf<Audiobook?>(null) }
        var clearError by remember { mutableStateOf<String?>(null) }
        var clearInFlight by remember { mutableStateOf(false) }
        val markScope = rememberCoroutineScope()
        val loading by viewModel.loading.collectAsState()
        val selectedView by viewModel.selectedView.collectAsState()
        val viewError by viewModel.viewError.collectAsState()
        val selectedSort by viewModel.selectedSort.collectAsState()
        val sortError by viewModel.sortError.collectAsState()
        val firstDetected by viewModel.firstDetected.collectAsState()
        val discoveryError by viewModel.discoveryError.collectAsState()
        var sortMenuOpen by remember { mutableStateOf(false) }
        val sortedBooks = remember(books, selectedSort, firstDetected) {
            sortedAllBooks(books, selectedSort, firstDetected)
        }
        val seriesLibrary = remember(books) { seriesLibraryFor(books) }
        val hasSeries = seriesLibrary.groups.isNotEmpty()
        val activeView = if (hasSeries) selectedView else LibraryView.AllBooks
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
                        modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
                    )
                    if (hasSeries) {
                        LibraryViewTabs(
                            selected = activeView,
                            onSelect = { view ->
                                if (view != activeView) {
                                    markScope.launch {
                                        val destination = if (view == LibraryView.Series) seriesScroll else allBooksScroll
                                        destination.scrollTo(0)
                                        viewModel.selectView(view)
                                    }
                                }
                            },
                        )
                    }
                    viewError?.let { message ->
                        LightText(
                            text = message,
                            variant = LightTextVariant.Detail,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                    }
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
                        key(activeView) {
                            LightScrollView(
                                scrollState = if (activeView == LibraryView.Series) seriesScroll else allBooksScroll,
                            ) {
                                if (!loading || books.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(start = 24.dp, end = 24.dp, bottom = 0.dp)
                                            .heightIn(min = 48.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        LightText(
                                            text = if (activeView == LibraryView.Series)
                                                seriesHeaderCount(seriesLibrary.groups.size) else libraryBookCountLabel(books),
                                            variant = LightTextVariant.Detail,
                                            // Match the sort control's vertical inset in both tabs.
                                            // This keeps the count baseline fixed when sort is absent.
                                            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                                        )
                                        if (activeView == LibraryView.AllBooks) {
                                            Row(
                                                modifier = Modifier
                                                    .onGloballyPositioned { sortAnchor = popupAnchorBounds(it) }
                                                    .lightClickable(onClickLabel = "Sort All Books") {
                                                        sortMenuOpen = true
                                                    }
                                                    .heightIn(min = 48.dp)
                                                    .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                LightText(text = librarySortLabel(selectedSort), variant = LightTextVariant.Paragraph)
                                                LightIcon(
                                                    icon = LightIcons.DOWN,
                                                    modifier = Modifier.offset(y = (-3).dp),
                                                    size = 1.0f,
                                                    contentDescription = null,
                                                )
                                            }
                                        }
                                    }
                                }
                                when {
                                    loading && books.isEmpty() -> StatusText("Scanning your library…")
                                    books.isEmpty() -> StatusText(
                                        "No books found. Copy audiobooks into the Audiobooks folder on your device.",
                                    )
                                    else -> {
                                        if (activeView == LibraryView.Series) {
                                            seriesLibrary.groups.forEachIndexed { groupIndex, group ->
                                                // Section boundaries use whitespace; dividers appear only between books.
                                                if (groupIndex > 0) Spacer(modifier = Modifier.height(22.dp))
                                                SeriesSectionHeader(name = group.name, count = group.books.size)
                                                group.books.forEachIndexed { bookIndex, book ->
                                                    BookRow(
                                                        book = book,
                                                        onOpen = { openPlayer(book) },
                                                        onMenu = { bounds ->
                                                            sortMenuOpen = false
                                                            menuAnchor = bounds
                                                            menuBook = book
                                                            markError = null
                                                        },
                                                        showSeriesLabel = false,
                                                        seriesNumber = seriesVolumeLabel(book),
                                                        showDivider = bookIndex < group.books.lastIndex,
                                                    )
                                                }
                                            }
                                            if (seriesLibrary.standaloneBooks.isNotEmpty()) {
                                                // Standalone books follow the same book-only divider rule.
                                                if (seriesLibrary.groups.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(22.dp))
                                                }
                                                SeriesSectionHeader(
                                                    name = "Standalone Books",
                                                    count = seriesLibrary.standaloneBooks.size,
                                                    multipleBooks = false,
                                                )
                                                seriesLibrary.standaloneBooks.forEachIndexed { bookIndex, book ->
                                                    BookRow(
                                                        book = book,
                                                        onOpen = { openPlayer(book) },
                                                        onMenu = { bounds ->
                                                            sortMenuOpen = false
                                                            menuAnchor = bounds
                                                            menuBook = book
                                                            markError = null
                                                        },
                                                        showSeriesLabel = false,
                                                        showDivider = bookIndex < seriesLibrary.standaloneBooks.lastIndex,
                                                    )
                                                }
                                            }
                                        } else {
                                            sortedBooks.forEachIndexed { index, book ->
                                                BookRow(
                                                    book = book,
                                                    onOpen = { openPlayer(book) },
                                                    onMenu = { bounds ->
                                                            sortMenuOpen = false
                                                            menuAnchor = bounds
                                                            menuBook = book
                                                            markError = null
                                                        },
                                                    showDivider = index < sortedBooks.lastIndex,
                                                )
                                            }
                                        }
                                    }
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
                            // the old offset. Series retains its own scroll.
                            markScope.launch { allBooksScroll.scrollTo(0) }
                        }
                    },
                )
            }
            menuBook?.let { selected ->
                BookActionsMenu(
                    anchorBounds = menuAnchor,
                    title = selected.title,
                    completed = selected.completed,
                    hasListeningProgress = selected.positionMilliseconds > 0L,
                    onClearProgress = {
                        menuBook = null
                        clearError = null
                        clearBook = selected
                    },
                    markError = markError,
                    markInFlight = markInFlight,
                    onMarkFinished = {
                        if (!markInFlight) {
                            markInFlight = true
                            markScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { AudiobookProgressStore.markFinished(selected) }
                                }
                                markInFlight = false
                                if (result.isSuccess) {
                                    menuBook = null
                                    markError = null
                                    viewModel.refresh()
                                } else {
                                    markError = "Could not mark this book finished. Please try again."
                                }
                            }
                        }
                    },
                    onDismiss = { if (!markInFlight) menuBook = null },
                    onEdit = {
                        menuBook = null
                        navigateTo(screenFactory = { EditBookDetailsScreen(it, selected) }) { saved ->
                            if (saved) viewModel.refresh()
                        }
                    },
                )
            }
            clearBook?.let { selected ->
                ClearProgressConfirmation(
                    title = selected.title,
                    inFlight = clearInFlight,
                    error = clearError,
                    onCancel = { if (!clearInFlight) clearBook = null },
                    onConfirm = {
                        if (!clearInFlight) {
                            clearInFlight = true
                            markScope.launch {
                                val result = runCatching {
                                    pauseAndRewindFromLibrary(DefaultLightAudio(audioActivity), selected)
                                    withContext(Dispatchers.IO) {
                                        AudiobookProgressStore.clearListeningProgress(selected)
                                    }
                                }
                                clearInFlight = false
                                if (result.isSuccess) {
                                    clearBook = null
                                    clearError = null
                                    viewModel.refresh()
                                } else {
                                    clearError = "Could not clear progress. No changes were reported as saved."
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    private fun openPlayer(book: Audiobook) {
        navigateTo(screenFactory = { PlayerScreen(it, book) })
    }

    private fun openSettings() {
        navigateTo(screenFactory = { SettingsScreen(it) })
    }
}

/** The four approved sorts, anchored to the sort trigger without scrim. */
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
            listOf(LibrarySort.TitleAz, LibrarySort.AuthorAz,
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
private fun LibraryViewTabs(selected: LibraryView, onSelect: (LibraryView) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 0.dp)) {
        LibraryViewTab(
            label = "ALL BOOKS",
            selected = selected == LibraryView.AllBooks,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(LibraryView.AllBooks) },
        )
        LibraryViewTab(
            label = "SERIES",
            selected = selected == LibraryView.Series,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(LibraryView.Series) },
        )
    }
}

@Composable
private fun LibraryViewTab(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.lightClickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LightText(
            text = label,
            variant = LightTextVariant.Fine,
            lighten = !selected,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) LightThemeTokens.colors.content else LightThemeTokens.colors.background),
        )
    }
}

/**
 * Lucide "library-big" and "book" (ISC license; see
 * tool/third_party/lucide-icons/LICENSE). Same 17dp visual weight as before.
 */
@Composable
private fun BookCollectionGlyph(multiple: Boolean) {
    val ink = LightThemeTokens.colors.content
    val strokes = remember(multiple) {
        if (multiple) listOf(
            PathParser().parsePathString("M7 3v18").toPath(),
            PathParser().parsePathString(
                "M20.4 18.9c.2.5-.1 1.1-.6 1.3l-1.9.7c-.5.2-1.1-.1-1.3-.6L11.1 5.1c-.2-.5.1-1.1.6-1.3l1.9-.7c.5-.2 1.1.1 1.3.6Z",
            ).toPath(),
        ) else listOf(
            PathParser().parsePathString(
                "M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H19a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H6.5a1 1 0 0 1 0-5H20",
            ).toPath(),
        )
    }
    Canvas(modifier = Modifier.size(17.dp)) {
        val ratio = size.minDimension / 24f
        withTransform({ scale(ratio, ratio, pivot = Offset.Zero) }) {
            val outline = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (multiple) {
                drawRoundRect(
                    color = ink,
                    topLeft = Offset(3f, 3f),
                    size = Size(8f, 18f),
                    cornerRadius = CornerRadius(1f, 1f),
                    style = outline,
                )
            }
            strokes.forEach { path -> drawPath(path, ink, style = outline) }
        }
    }
}

@Composable
private fun SeriesSectionHeader(name: String, count: Int, multipleBooks: Boolean = true) {
    Row(
        // Section label: hierarchy comes from the gap, icon and SDK Fine
        // typography, not an oversized headline.
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCollectionGlyph(multiple = multipleBooks)
        Spacer(modifier = Modifier.width(7.dp))
        LightText(
            text = name,
            variant = LightTextVariant.Fine,
            modifier = Modifier.weight(1f),
        )
        LightText(
            text = seriesGroupBookCount(count),
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(start = 8.dp),
        )
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

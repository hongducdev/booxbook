package com.booxbook.feature.reader

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.booxbook.core.engine.model.ReaderThemePalette
import com.booxbook.core.engine.cbz.CbzReaderComponent
import com.booxbook.core.model.BookFormat
import com.booxbook.core.ui.component.ExpressivePillButton
import com.booxbook.core.ui.theme.GoogleSansFlexDisplay
import com.booxbook.feature.reader.components.AnimatedReaderTopBar
import com.booxbook.feature.reader.components.BookendsOverlay
import com.booxbook.feature.reader.components.BookendsSettingsSheet
import com.booxbook.feature.reader.components.BookmarksSheet
import com.booxbook.feature.reader.components.EpubReaderContainer
import com.booxbook.feature.reader.components.FloatingReaderToolbar
import com.booxbook.feature.reader.components.PageTurnFlipOverlay
import com.booxbook.feature.reader.components.ReadingFrameLayer
import com.booxbook.feature.reader.components.ReaderOpeningOverlay
import com.booxbook.feature.reader.components.ReaderSettingsSheet
import com.booxbook.feature.reader.components.TableOfContentsSheet
import com.booxbook.feature.reader.components.TapZonePreviewOverlay
import com.booxbook.feature.reader.components.TtsFloatingPlayer
import com.booxbook.feature.reader.components.rememberPageTurnFlipController
import com.booxbook.feature.reader.bookends.BookendsViewModel
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url

@Composable
fun ReaderScreen(
    bookId: String,
    initialLocator: String? = null,
    onBackClick: () -> Unit = {},
    viewModel: ReaderViewModel = hiltViewModel(),
    bookendsViewModel: BookendsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bookendsState by bookendsViewModel.uiState.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsSessionState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var activeEpubNavigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }

    val flipController = rememberPageTurnFlipController()
    var showTapZonePreview by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val tapZoneMode = ReaderTapZoneMode.fromKey(uiState.preferences.tapZoneMode)
    val pageTurnEffect = ReaderPageTurnEffect.fromKey(uiState.preferences.pageTurnEffect)

    // Mọi thao tác đều trả lời người đọc: snackbar là kênh phản hồi duy nhất của màn đọc, và các sự
    // kiện có thể hoàn tác (xoá ghi chú) mang theo bản ghi gốc để nút "Hoàn tác" khôi phục lại.
    LaunchedEffect(viewModel) {
        viewModel.feedback.collect { feedback ->
            val snackbar = feedback.toSnackbar()
            val result = snackbarHostState.showSnackbar(
                message = snackbar.message,
                actionLabel = snackbar.actionLabel,
                duration = SnackbarDuration.Short
            )
            val undoAnnotation = snackbar.undoAnnotation
            if (result == SnackbarResult.ActionPerformed && undoAnnotation != null) {
                viewModel.restoreAnnotation(undoAnnotation)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.pauseReadingSession()
                Lifecycle.Event.ON_RESUME -> viewModel.resumeReadingSession()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushReadingSession(isEnding = true)
        }
    }

    val performPageTurn: (Boolean) -> Unit = { forward ->
        viewModel.recordUserInteraction()
        val navigator = activeEpubNavigator
        if (navigator != null) {
            // Kindle-like lift: snapshot the outgoing page first, then turn instantly and peel it away.
            val publicationView: View? = navigator.publicationView
            val flipStarted = pageTurnEffect == ReaderPageTurnEffect.FLIP &&
                publicationView != null &&
                flipController.play(publicationView, forward)

            val animated = pageTurnEffect != ReaderPageTurnEffect.NONE && !flipStarted
            if (forward) {
                navigator.goForward(animated = animated)
            } else {
                navigator.goBackward(animated = animated)
            }

            if (uiState.preferences.hapticsEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    LaunchedEffect(bookId, initialLocator) {
        viewModel.loadBook(bookId, targetLocator = initialLocator)
    }

    // Overlay chỉ *đọc* trạng thái, nên màn đọc đẩy vào đúng hai thứ nó có: vị trí đang đọc và tiến trình
    // phiên. `MutableStateFlow` phía nhận tự bỏ qua giá trị trùng, nên đẩy mỗi recomposition là an toàn.
    LaunchedEffect(viewModel, bookendsViewModel) {
        viewModel.bookendsContext.collect(bookendsViewModel::onReadingContextChanged)
    }

    LaunchedEffect(viewModel, bookendsViewModel) {
        viewModel.bookendsSessionProgress.collect(bookendsViewModel::onSessionProgressChanged)
    }

    BackHandler {
        when {
            uiState.activeSheet != null -> viewModel.dismissSheet()
            uiState.isControlsVisible -> viewModel.hideControls()
            else -> onBackClick()
        }
    }

    val backgroundColor = Color(ReaderThemePalette.backgroundArgb(uiState.themePreset.name).toInt())

    // Bookends không dùng màu theme Material: nó nằm trên nền trang giấy của chính người đọc, nên độ tương
    // phản phải tính theo nền đó, không theo màu surface của ứng dụng. Cùng bảng màu với trang sách.
    val isDarkReader = ReaderThemePalette.isDark(uiState.themePreset.name)
    val bookendsContentColor = Color(
        ReaderThemePalette.textArgb(uiState.themePreset.name).toInt()
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        containerColor = backgroundColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Canvas được dựng ngay khi đã biết sách, kể cả lúc màn chờ còn phủ bên trên: Readium chỉ
            // báo sẵn sàng sau khi navigator được gắn thật vào cây view, nên nếu đợi màn chờ tan mới
            // dựng canvas thì sẽ không bao giờ có tín hiệu — và người đọc thấy một khoảng trắng.
            val hasCanvas = uiState.book != null && uiState.errorMessage == null

            if (hasCanvas) {
                val readerInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

                // Padded reading canvas: insets protect text from status bar and navigation bar cutouts.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(readerInsets)
                ) {
                    val preferences = uiState.preferences

                    // Lề áp cho **trang sách** bằng padding Compose, cho cả ba định dạng.
                    //
                    // Readium chỉ có một `pageMargins` cho cả bốn phía nên không đủ cho nhu cầu thật là chừa chỗ
                    // khác nhau ở trên và ở dưới. Đổi lại, padding làm WebView đổi kích thước mà Readium không tự
                    // biết — `EpubReaderContainer` bù bằng cách gọi `submitPreferences` sau mỗi lần đổi kích thước.
                    val pagePadding = PaddingValues(
                        start = preferences.marginHorizontalDp.dp,
                        top = preferences.marginTopDp.dp,
                        end = preferences.marginHorizontalDp.dp,
                        bottom = preferences.marginBottomDp.dp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(pagePadding)
                    ) {
                    // Content Canvas: EPUB vs CBZ
                    if (uiState.format == BookFormat.CBZ) {
                        val archive = viewModel.cbzReaderEngine.getArchive()
                        if (archive != null) {
                            CbzReaderComponent(
                                archive = archive,
                                initialPageIndex = uiState.currentPage,
                                onPageChanged = { pageIndex, totalPages ->
                                    viewModel.recordUserInteraction()
                                    viewModel.onPageChanged(
                                        pageIndex = pageIndex,
                                        totalPages = totalPages,
                                        chapterTitle = "Trang ${pageIndex + 1}"
                                    )
                                },
                                onTap = {
                                    viewModel.recordUserInteraction()
                                    viewModel.toggleControls()
                                },
                                onReady = viewModel::onCanvasReady,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        // EPUB / AZW3 Readium Navigator
                        val readiumEngine = viewModel.getActiveReadiumEngine()
                        if (readiumEngine != null) {
                            EpubReaderContainer(
                                epubEngine = readiumEngine,
                                preferences = uiState.preferences,
                                initialLocatorJson = uiState.currentLocator,
                                ttsSentenceHighlight = uiState.ttsSentenceHighlight,
                                ttsSentenceLocatorJson = uiState.ttsSentenceLocator,
                                isTtsPlaying = ttsState.isPlaying,
                                themePreset = uiState.themePreset,
                                onLocatorChanged = { locator ->
                                    // Locator đầu tiên cũng là mốc trang đọc đã vẽ xong.
                                    viewModel.onCanvasReady()
                                    viewModel.recordUserInteraction()
                                    val totalProg = locator.locations.totalProgression?.toFloat()
                                        ?: (locator.locations.progression?.toFloat() ?: 0f)
                                    val pageIndex = locator.locations.position ?: 0

                                    viewModel.onPageChanged(
                                        pageIndex = pageIndex,
                                        totalPages = uiState.totalPages,
                                        locator = locator.toJSON().toString(),
                                        chapterTitle = locator.title ?: "",
                                        percentage = totalProg
                                    )
                                },
                                onTapAction = { action ->
                                    viewModel.recordUserInteraction()
                                    when (action) {
                                        ReaderTapAction.NEXT -> performPageTurn(true)
                                        ReaderTapAction.PREV -> performPageTurn(false)
                                        ReaderTapAction.MENU -> viewModel.toggleControls()
                                        ReaderTapAction.NONE -> Unit
                                    }
                                },
                                onNavigatorReady = { nav -> activeEpubNavigator = nav },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Đường viền vẽ **lên trên** vùng đọc và không tham gia bố cục, nên hai cài đặt lề và viền
                    // độc lập nhau: tăng lề để chữ không chạm viền, còn viền vẫn nằm nguyên chỗ đã đặt.
                    // Nằm trên canvas nhưng dưới lớp lật trang và overlay chữ.
                    ReadingFrameLayer(
                        frame = preferences.frame,
                        isDarkReader = isDarkReader,
                        modifier = Modifier.matchParentSize()
                    )

                    // Kindle-like page-lift transition: above the canvas, below the chrome.
                    PageTurnFlipOverlay(
                        controller = flipController,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Transient tap-zone preview, triggered from reader settings.
                    // Ở trong hộp đã chừa lề vì nó vẽ đúng vùng chạm của canvas.
                    TapZonePreviewOverlay(
                        mode = tapZoneMode,
                        visible = showTapZonePreview,
                        onDismissed = { showTapZonePreview = false },
                        modifier = Modifier.fillMaxSize()
                    )
                    }

                    // Bookends nằm **ngoài** hộp đã chừa lề: nó neo theo màn đọc, không theo trang, nên đổi lề
                    // không làm nó chạy. Đặt trong hộp đó sẽ khiến cả lớp thông tin trôi theo lề — đã quan sát
                    // trên máy.
                    //
                    // Nằm cùng tầng với hiệu ứng lật trang: trên chữ, dưới thanh công cụ. Ẩn khi thanh công cụ
                    // mở ra, vì lúc đó overlay và chrome sẽ tranh nhau cùng một dải màn hình.
                    bookendsState.preset?.let { preset ->
                        bookendsState.snapshot?.let { snapshot ->
                            BookendsOverlay(
                                preset = preset,
                                snapshot = snapshot,
                                visible = !uiState.isControlsVisible && uiState.activeSheet == null,
                                contentColor = bookendsContentColor,
                                trackColor = bookendsContentColor.copy(alpha = 0.22f),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Top App Bar
                AnimatedReaderTopBar(
                    visible = uiState.isControlsVisible,
                    title = uiState.book?.title ?: "",
                    subtitle = uiState.currentChapterTitle,
                    isBookmarked = uiState.isCurrentLocationBookmarked,
                    onBackClick = onBackClick,
                    onBookmarkToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleBookmark()
                    },
                    isTtsSupported = uiState.format != BookFormat.CBZ,
                    isTtsActive = uiState.isTtsActive,
                    onTtsToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (uiState.isTtsActive) {
                            viewModel.stopTts()
                        } else {
                            viewModel.startTts(context)
                        }
                    },
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // Floating Bottom Toolbar
                FloatingReaderToolbar(
                    visible = uiState.isControlsVisible,
                    currentPage = uiState.currentPage,
                    totalPages = uiState.totalPages,
                    progressPercentage = uiState.progressPercentage,
                    onSeekToPage = { page ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (uiState.format == BookFormat.CBZ) {
                            viewModel.onPageChanged(page, uiState.totalPages)
                        } else {
                            val nav = activeEpubNavigator
                            val publication = viewModel.getActiveReadiumEngine()?.getPublication()
                            if (nav != null && publication != null && uiState.totalPages > 0) {
                                val targetSpineIndex = (page).coerceIn(0, publication.readingOrder.size - 1)
                                val link = publication.readingOrder.getOrNull(targetSpineIndex)
                                if (link != null) {
                                    nav.go(link, animated = false)
                                }
                            }
                        }
                    },
                    onOpenToc = { viewModel.openSheet(ActiveReaderSheet.TOC) },
                    onOpenSettings = { viewModel.openSheet(ActiveReaderSheet.SETTINGS) },
                    onOpenBookmarks = { viewModel.openSheet(ActiveReaderSheet.BOOKMARKS) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // TTS Floating Play/Pause Button (Draggable single FAB, always available)
                TtsFloatingPlayer(
                    visible = uiState.book != null && uiState.format != BookFormat.CBZ && !uiState.isLoading && uiState.errorMessage == null,
                    state = ttsState,
                    onTogglePlayPause = {
                        if (!uiState.isTtsActive || !ttsState.isActive || ttsState.totalSentences == 0) {
                            viewModel.startTts(context)
                        } else {
                            viewModel.toggleTtsPlayPause()
                        }
                    },
                    onNextSentence = viewModel::nextTtsSentence,
                    onPreviousSentence = viewModel::previousTtsSentence,
                    onSpeedSelected = viewModel::setTtsSpeed,
                    onClose = viewModel::stopTts,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 32.dp)
                )

                // Sheets
                when (uiState.activeSheet) {
                    ActiveReaderSheet.TOC -> {
                        TableOfContentsSheet(
                            tableOfContents = uiState.tableOfContents,
                            currentHref = uiState.currentLocator,
                            onItemClick = { item ->
                                viewModel.dismissSheet()
                                if (uiState.format == BookFormat.CBZ) {
                                    val pageNum = item.href.substringAfter("page://", "0").toIntOrNull() ?: 0
                                    viewModel.onPageChanged(pageNum, uiState.totalPages, chapterTitle = item.title)
                                } else {
                                    val nav = activeEpubNavigator
                                    val url = Url(item.href)
                                    if (nav != null && url != null) {
                                        nav.go(Link(href = Href(url)), animated = true)
                                    }
                                }
                            },
                            onDismiss = viewModel::dismissSheet
                        )
                    }

                    ActiveReaderSheet.SETTINGS -> {
                        ReaderSettingsSheet(
                            preferences = uiState.preferences,
                            themePreset = uiState.themePreset,
                            tapZoneMode = tapZoneMode,
                            pageTurnEffect = pageTurnEffect,
                            hapticsEnabled = uiState.preferences.hapticsEnabled,
                            onFontSizeDelta = viewModel::updateFontSize,
                            onFontFamilySelected = viewModel::updateFontFamily,
                            onThemePresetSelected = viewModel::updateThemePreset,
                            onTapZoneModeSelected = viewModel::updateTapZoneMode,
                            onPageTurnEffectSelected = viewModel::updatePageTurnEffect,
                            onHapticsToggled = viewModel::updateHapticsEnabled,
                            onPreviewTapZones = {
                                viewModel.dismissSheet()
                                showTapZonePreview = true
                            },
                            onOpenBookends = { viewModel.openSheet(ActiveReaderSheet.BOOKENDS) },
                            onMarginChange = viewModel::updateMargin,
                            onResetMargins = viewModel::resetMargins,
                            onFrameChanged = { frame -> viewModel.updateFrame { frame } },
                            onDismiss = viewModel::dismissSheet
                        )
                    }

                    ActiveReaderSheet.BOOKENDS -> {
                        BookendsSettingsSheet(
                            settings = bookendsState.settings,
                            snapshot = bookendsState.snapshot,
                            onCreatePreset = bookendsViewModel::createPresetFromActive,
                            onPresetUpdated = { bookendsViewModel.upsertPreset(it) },
                            onPresetSelected = bookendsViewModel::setActivePreset,
                            onPresetReset = bookendsViewModel::resetPreset,
                            onPresetDelete = bookendsViewModel::deletePreset,
                            onEnabledChange = bookendsViewModel::setEnabled,
                            onAutoRuleSet = bookendsViewModel::setAutoRule,
                            onAutoRuleRemoved = bookendsViewModel::removeAutoRule,
                            onDismiss = viewModel::dismissSheet
                        )
                    }

                    ActiveReaderSheet.BOOKMARKS -> {
                        BookmarksSheet(
                            bookmarks = uiState.annotations,
                            onBookmarkClick = { annotation ->
                                viewModel.dismissSheet()
                                if (uiState.format == BookFormat.CBZ) {
                                    val pageNum = annotation.locator.substringAfter("page://", "0").toIntOrNull() ?: 0
                                    viewModel.onPageChanged(pageNum, uiState.totalPages, chapterTitle = annotation.noteContent ?: "Trang đánh dấu")
                                } else {
                                    val nav = activeEpubNavigator
                                    runCatching {
                                        val loc = Locator.fromJSON(org.json.JSONObject(annotation.locator))
                                        if (nav != null && loc != null) {
                                            nav.go(loc, animated = true)
                                        }
                                    }
                                }
                            },
                            onDeleteClick = viewModel::deleteAnnotation,
                            onDismiss = viewModel::dismissSheet
                        )
                    }

                    null -> {}
                }
            }

            // Màn chờ có ngữ cảnh: phủ trên canvas đang dựng ngầm và tan dần khi trang đầu hiện ra.
            ReaderOpeningOverlay(
                visible = uiState.isPreparing,
                phase = uiState.loadingPhase,
                book = uiState.book,
                backgroundColor = backgroundColor,
                modifier = Modifier.fillMaxSize()
            )

            if (uiState.errorMessage != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.errorMessage ?: "Đã có lỗi xảy ra",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = GoogleSansFlexDisplay,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        ExpressivePillButton(
                            onClick = onBackClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Quay lại thư viện")
                        }
                    }
                }
            }
        }
    }
}

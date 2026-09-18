package com.booxbook.feature.reader

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.booxbook.core.engine.cbz.CbzReaderComponent
import com.booxbook.core.model.BookFormat
import com.booxbook.core.ui.component.ExpressivePillButton
import com.booxbook.core.ui.theme.AmoledBackground
import com.booxbook.core.ui.theme.GoogleSansFlex400
import com.booxbook.core.ui.theme.GoogleSansFlexDisplay
import com.booxbook.core.ui.theme.SepiaBackground
import com.booxbook.feature.reader.components.AnimatedReaderTopBar
import com.booxbook.feature.reader.components.BookmarksSheet
import com.booxbook.feature.reader.components.EpubReaderContainer
import com.booxbook.feature.reader.components.FloatingReaderToolbar
import com.booxbook.feature.reader.components.PageTurnFlipOverlay
import com.booxbook.feature.reader.components.ReaderSettingsSheet
import com.booxbook.feature.reader.components.TableOfContentsSheet
import com.booxbook.feature.reader.components.TapZonePreviewOverlay
import com.booxbook.feature.reader.components.TtsFloatingPlayer
import com.booxbook.feature.reader.components.rememberPageTurnFlipController
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
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsSessionState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var activeEpubNavigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }

    val flipController = rememberPageTurnFlipController()
    var showTapZonePreview by remember { mutableStateOf(false) }

    val tapZoneMode = ReaderTapZoneMode.fromKey(uiState.preferences.tapZoneMode)
    val pageTurnEffect = ReaderPageTurnEffect.fromKey(uiState.preferences.pageTurnEffect)

    val performPageTurn: (Boolean) -> Unit = { forward ->
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

    BackHandler {
        when {
            uiState.activeSheet != null -> viewModel.dismissSheet()
            uiState.isControlsVisible -> viewModel.hideControls()
            else -> onBackClick()
        }
    }

    val backgroundColor = when (uiState.themePreset) {
        ReaderThemePreset.LIGHT -> Color(0xFFFEF7FF)
        ReaderThemePreset.SEPIA -> SepiaBackground
        ReaderThemePreset.DARK -> Color(0xFF141218)
        ReaderThemePreset.AMOLED -> AmoledBackground
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        containerColor = backgroundColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Đang mở sách...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = GoogleSansFlex400
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                uiState.errorMessage != null -> {
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

                else -> {
                    val readerInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

                    // Padded reading canvas: insets protect text from status bar and navigation bar cutouts.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(readerInsets)
                    ) {
                        // Content Canvas: EPUB vs CBZ
                        if (uiState.format == BookFormat.CBZ) {
                            val archive = viewModel.cbzReaderEngine.getArchive()
                            if (archive != null) {
                                CbzReaderComponent(
                                    archive = archive,
                                    initialPageIndex = uiState.currentPage,
                                    onPageChanged = { pageIndex, totalPages ->
                                        viewModel.onPageChanged(
                                            pageIndex = pageIndex,
                                            totalPages = totalPages,
                                            chapterTitle = "Trang ${pageIndex + 1}"
                                        )
                                    },
                                    onTap = { viewModel.toggleControls() },
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
                                    onLocatorChanged = { locator ->
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

                        // Kindle-like page-lift transition: above the canvas, below the chrome.
                        PageTurnFlipOverlay(
                            controller = flipController,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Transient tap-zone preview, triggered from reader settings.
                        TapZonePreviewOverlay(
                            mode = tapZoneMode,
                            visible = showTapZonePreview,
                            onDismissed = { showTapZonePreview = false },
                            modifier = Modifier.fillMaxSize()
                        )
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
                                    val targetProgression = (page.toDouble() / (uiState.totalPages - 1).coerceAtLeast(1)).coerceIn(0.0, 1.0)
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

                    // TTS Floating Mini-Player
                    TtsFloatingPlayer(
                        visible = uiState.isTtsActive,
                        state = ttsState,
                        onTogglePlayPause = viewModel::toggleTtsPlayPause,
                        onNextSentence = viewModel::nextTtsSentence,
                        onPreviousSentence = viewModel::previousTtsSentence,
                        onSpeedSelected = viewModel::setTtsSpeed,
                        onClose = viewModel::stopTts,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (uiState.isControlsVisible) 96.dp else 16.dp)
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
            }
        }
    }
}

package com.booxbook.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.booxbook.feature.reader.components.ReaderSettingsSheet
import com.booxbook.feature.reader.components.TableOfContentsSheet
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url

@Composable
fun ReaderScreen(
    bookId: String,
    onBackClick: () -> Unit = {},
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var activeEpubNavigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
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
                        EpubReaderContainer(
                            epubEngine = viewModel.epubReaderEngine,
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
                            onCenterTap = { viewModel.toggleControls() },
                            onNavigatorReady = { nav -> activeEpubNavigator = nav },
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
                        onBookmarkToggle = viewModel::toggleBookmark,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    // Floating Bottom Toolbar
                    FloatingReaderToolbar(
                        visible = uiState.isControlsVisible,
                        currentPage = uiState.currentPage,
                        totalPages = uiState.totalPages,
                        progressPercentage = uiState.progressPercentage,
                        onSeekToPage = { page ->
                            if (uiState.format == BookFormat.CBZ) {
                                viewModel.onPageChanged(page, uiState.totalPages)
                            } else {
                                val nav = activeEpubNavigator
                                val publication = viewModel.epubReaderEngine.getPublication()
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
                                onFontSizeDelta = viewModel::updateFontSize,
                                onFontFamilySelected = viewModel::updateFontFamily,
                                onThemePresetSelected = viewModel::updateThemePreset,
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

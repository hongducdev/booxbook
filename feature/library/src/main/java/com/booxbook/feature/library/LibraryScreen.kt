package com.booxbook.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.booxbook.core.model.BookFormat
import com.booxbook.core.ui.component.ExpressiveFilterChip
import com.booxbook.core.ui.theme.GoogleSansFlex400
import com.booxbook.core.ui.theme.GoogleSansFlex600
import com.booxbook.core.ui.theme.GoogleSansFlexDisplay
import com.booxbook.core.ui.theme.PillShape
import com.booxbook.feature.library.components.BentoBookCard
import com.booxbook.feature.library.components.BookDetailBottomSheet
import com.booxbook.feature.library.components.ContinueReadingCarousel
import com.booxbook.feature.library.components.EmptyLibraryPlaceholder

private val SUPPORTED_BOOK_MIME_TYPES = arrayOf(
    "application/epub+zip",
    "application/x-cbz",
    "application/zip",
    "application/octet-stream",
    "*/*"
)

@Composable
fun LibraryScreen(
    onBookClick: (String) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // SAF Document Picker for Books
    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importBooksFromUris(uris)
        }
    }

    // Handle Import & User Messages
    LaunchedEffect(uiState.importState) {
        when (val state = uiState.importState) {
            is ImportState.Success -> {
                val msg = if (state.count > 1) "Đã thêm thành công ${state.count} cuốn sách" else "Đã thêm \"${state.lastBook.title}\""
                val addedBookId = state.lastBook.id
                viewModel.clearImportState()
                val result = snackbarHostState.showSnackbar(
                    message = msg,
                    actionLabel = "Mở sách",
                    duration = androidx.compose.material3.SnackbarDuration.Short
                )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    onBookClick(addedBookId)
                }
            }
            is ImportState.BatchResult -> {
                val msg = "Đã thêm ${state.successCount} sách (${state.errors.size} lỗi)"
                viewModel.clearImportState()
                snackbarHostState.showSnackbar(msg)
            }
            is ImportState.Error -> {
                val msg = "Lỗi: ${state.message}"
                viewModel.clearImportState()
                snackbarHostState.showSnackbar(msg)
            }
            else -> {}
        }
    }

    LaunchedEffect(uiState.userMessage) {
        val msg = uiState.userMessage
        if (msg != null) {
            viewModel.clearUserMessage()
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { documentPicker.launch(SUPPORTED_BOOK_MIME_TYPES) },
                shape = PillShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Thêm sách",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header & App Title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BooxBook",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = GoogleSansFlexDisplay,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Surface(
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "${uiState.books.size} cuốn sách",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = GoogleSansFlex600,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Search Bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    TextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "Tìm kiếm tựa đề hoặc tác giả...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = GoogleSansFlex400
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "Xóa tìm kiếm",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Expressive Filter Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpressiveFilterChip(
                        selected = uiState.selectedFormat == null,
                        onClick = { viewModel.onFormatSelected(null) },
                        label = "Tất cả"
                    )
                    ExpressiveFilterChip(
                        selected = uiState.selectedFormat == BookFormat.EPUB,
                        onClick = { viewModel.onFormatSelected(BookFormat.EPUB) },
                        label = "EPUB"
                    )
                    ExpressiveFilterChip(
                        selected = uiState.selectedFormat == BookFormat.CBZ,
                        onClick = { viewModel.onFormatSelected(BookFormat.CBZ) },
                        label = "Truyện tranh (CBZ)"
                    )
                    ExpressiveFilterChip(
                        selected = uiState.selectedFormat == BookFormat.AZW3,
                        onClick = { viewModel.onFormatSelected(BookFormat.AZW3) },
                        label = "Kindle (AZW3)"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Main Library Content
                if (uiState.isEmpty || uiState.isSearchEmpty) {
                    EmptyLibraryPlaceholder(
                        isSearchEmpty = uiState.isSearchEmpty,
                        onAddBookClick = { documentPicker.launch(SUPPORTED_BOOK_MIME_TYPES) }
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Section: Continue Reading Carousel (only when not searching / filtering)
                        if (uiState.recentBooks.isNotEmpty() && uiState.searchQuery.isBlank() && uiState.selectedFormat == null) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                ContinueReadingCarousel(
                                    recentBooks = uiState.recentBooks,
                                    onBookClick = { item -> onBookClick(item.book.id) },
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }
                        }

                        // Section Header: All Books
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "Tất cả sách (${uiState.filteredBooks.size})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = GoogleSansFlexDisplay,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }

                        // Grid Items: Bento Cards
                        items(
                            items = uiState.filteredBooks,
                            key = { it.book.id }
                        ) { item ->
                            BentoBookCard(
                                item = item,
                                onClick = { onBookClick(item.book.id) },
                                onLongClick = { viewModel.onBookSelectedForDetail(item) }
                            )
                        }
                    }
                }
            }

            // Importing Loader Overlay
            val currentImport = uiState.importState
            AnimatedVisibility(
                visible = currentImport is ImportState.Importing,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                val importText = if (currentImport is ImportState.Importing && currentImport.total > 1) {
                    "Đang nhập sách (${currentImport.current}/${currentImport.total})..."
                } else {
                    "Đang xử lý nhập sách..."
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = importText,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Detail Bottom Sheet
            val selectedItem = uiState.selectedBookForDetail
            if (selectedItem != null) {
                BookDetailBottomSheet(
                    item = selectedItem,
                    onReadClick = { onBookClick(selectedItem.book.id) },
                    onDeleteClick = { viewModel.deleteBook(selectedItem.book) },
                    onDismiss = viewModel::dismissDetail
                )
            }
        }
    }
}

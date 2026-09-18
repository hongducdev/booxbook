package com.booxbook.feature.library.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.booxbook.core.engine.model.TocItem
import com.booxbook.core.model.Book
import com.booxbook.core.model.ReadingProgress
import com.booxbook.core.ui.component.ExpressivePillButton
import com.booxbook.core.ui.theme.GoogleSansFlex400
import com.booxbook.core.ui.theme.GoogleSansFlex600
import com.booxbook.core.ui.theme.GoogleSansFlexDisplay
import com.booxbook.core.ui.theme.PillShape
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: String,
    onBackClick: () -> Unit,
    onReadClick: (bookId: String, locator: String?) -> Unit,
    onBookDeleted: () -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Chi tiết sách",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = GoogleSansFlexDisplay,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                },
                actions = {
                    if (uiState.book != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Xóa sách",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
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
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Đang tải thông tin sách...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = GoogleSansFlex400
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                uiState.errorMessage != null && uiState.book == null -> {
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
                            ExpressivePillButton(onClick = onBackClick) {
                                Text("Quay lại")
                            }
                        }
                    }
                }

                uiState.book != null -> {
                    val book = uiState.book!!
                    val progress = uiState.progress

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 36.dp)
                    ) {
                        // 1. Header Overview (Bìa, Tiêu đề, Tác giả, Badges)
                        item {
                            BookOverviewSection(
                                book = book,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                            )
                        }

                        // 2. Reading Progress Card
                        item {
                            ReadingProgressSection(
                                progress = progress,
                                isFinished = uiState.isFinished,
                                hasStarted = uiState.hasStartedReading,
                                onResetClick = { showResetDialog = true },
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }

                        // 3. Primary Action Button (Đọc tiếp / Bắt đầu đọc)
                        item {
                            val buttonText = if (uiState.hasStartedReading) {
                                "Đọc tiếp (${uiState.percentageInt}%)"
                            } else {
                                "Bắt đầu đọc"
                            }

                            ExpressivePillButton(
                                onClick = { onReadClick(book.id, null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                                    .height(54.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = if (uiState.hasStartedReading) {
                                        Icons.AutoMirrored.Rounded.MenuBook
                                    } else {
                                        Icons.Rounded.PlayArrow
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = buttonText,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = GoogleSansFlex600,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        // 4. Section Divider & Table of Contents Header
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.FormatListBulleted,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Mục lục sách",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = GoogleSansFlexDisplay,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                if (uiState.isTocLoading) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Đang tải...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = PillShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Text(
                                            text = "${uiState.tableOfContents.size} mục",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = GoogleSansFlex600,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }

                        // 5. Table of Contents Items
                        if (!uiState.isTocLoading && uiState.tableOfContents.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Sách này không chứa danh mục mục lục phân đoạn.",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = GoogleSansFlex400
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(
                                items = uiState.tableOfContents,
                                key = { it.href }
                            ) { tocItem ->
                                DetailTocItemRow(
                                    item = tocItem,
                                    currentLocator = progress?.locator,
                                    depth = 0,
                                    onItemClick = { clickedItem ->
                                        onReadClick(book.id, clickedItem.href)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Dialog xác nhận xóa sách
            if (showDeleteDialog && uiState.book != null) {
                val currentBook = uiState.book!!
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "Xóa cuốn sách này?",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = GoogleSansFlexDisplay,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    },
                    text = {
                        Text(
                            text = "Bạn có chắc chắn muốn xóa \"${currentBook.title}\" khỏi thiết bị? Mọi dữ liệu tiến độ và ghi chú sẽ bị xóa vĩnh viễn.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteDialog = false
                                viewModel.deleteBook(onSuccess = onBookDeleted)
                            }
                        ) {
                            Text("Xóa", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text("Hủy")
                        }
                    }
                )
            }

            // Dialog xác nhận đặt lại tiến độ
            if (showResetDialog) {
                AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    title = { Text("Đặt lại tiến độ đọc?") },
                    text = { Text("Tiến độ đọc sẽ được đưa về trang đầu tiên (0%). Bạn có muốn tiếp tục?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showResetDialog = false
                                viewModel.resetReadingProgress()
                            }
                        ) {
                            Text("Đặt lại", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetDialog = false }) {
                            Text("Hủy")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun BookOverviewSection(
    book: Book,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Ảnh bìa sách
        Box(
            modifier = Modifier
                .width(115.dp)
                .aspectRatio(0.72f)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val coverPath = book.coverPath
            if (!coverPath.isNullOrBlank() && File(coverPath).exists()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(coverPath))
                        .crossfade(true)
                        .build(),
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = book.format.displayName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(18.dp))

        // Cột thông tin chi tiết
        Column(modifier = Modifier.weight(1f)) {
            // Định dạng badge
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = book.format.displayName,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = GoogleSansFlex600,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tựa đề
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = GoogleSansFlexDisplay,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Tác giả
            Text(
                text = book.author.ifBlank { "Tác giả chưa rõ" },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = GoogleSansFlex400
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Dung lượng tệp
            Text(
                text = "Dung lượng: ${formatFileSize(book.fileSize)}",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = GoogleSansFlex400),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Ngày thêm
            val addedDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(book.addedTimestamp))
            Text(
                text = "Thêm ngày: $addedDate",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = GoogleSansFlex400),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReadingProgressSection(
    progress: ReadingProgress?,
    isFinished: Boolean,
    hasStarted: Boolean,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val percentage = progress?.percentage ?: 0f
    val percentageInt = (percentage * 100).toInt().coerceIn(0, 100)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoStories,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tiến độ đọc",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = GoogleSansFlex600,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "$percentageInt%",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = GoogleSansFlexDisplay,
                        fontWeight = FontWeight.Black
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { percentage.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val detailText = when {
                    isFinished -> "Bạn đã hoàn thành cuốn sách này!"
                    hasStarted -> {
                        val pages = if (progress != null && progress.totalPages > 0) {
                            "Trang ${progress.currentPage + 1} / ${progress.totalPages}"
                        } else {
                            "Đã đọc được $percentageInt%"
                        }
                        pages
                    }
                    else -> "Chưa bắt đầu đọc"
                }

                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = GoogleSansFlex400
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (hasStarted) {
                    Text(
                        text = "Đọc lại từ đầu",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = GoogleSansFlex600,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(PillShape)
                            .clickable { onResetClick() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailTocItemRow(
    item: TocItem,
    currentLocator: String?,
    depth: Int,
    onItemClick: (TocItem) -> Unit
) {
    val isCurrentChapter = currentLocator != null && currentLocator.contains(item.href)

    Surface(
        color = if (isCurrentChapter) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            Color.Transparent
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick(item) }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = (20 + depth * 16).dp,
                        end = 20.dp,
                        top = 12.dp,
                        bottom = 12.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = if (isCurrentChapter) GoogleSansFlex600 else GoogleSansFlex400,
                        fontWeight = if (isCurrentChapter) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isCurrentChapter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isCurrentChapter) {
                    Surface(
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "Đang đọc",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Đệ quy hiển thị các chương con nếu có
            item.children.forEach { child ->
                DetailTocItemRow(
                    item = child,
                    currentLocator = currentLocator,
                    depth = depth + 1,
                    onItemClick = onItemClick
                )
            }
        }
    }
}

private fun formatFileSize(sizeInBytes: Long): String {
    if (sizeInBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val df = DecimalFormat("#,##0.#")
    return "${df.format(sizeInBytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}

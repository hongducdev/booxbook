package com.booxbook.core.engine.cbz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.booxbook.core.ui.component.ExpressiveContainedLoadingIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Comic/Manga reader component implementing vertical continuous scroll (Webtoon style)
 * with pinch-to-zoom and double-tap zoom capabilities.
 *
 * [onReady] báo rằng trang đầu tiên đã giải nén xong và sẵn sàng hiển thị. Màn đọc dùng tín hiệu này
 * để tan lớp phủ "đang mở sách" đúng lúc nội dung thật sự có mặt, thay vì đoán bằng một khoảng chờ.
 */
@Composable
fun CbzReaderComponent(
    archive: CbzArchive,
    modifier: Modifier = Modifier,
    initialPageIndex: Int = 0,
    onPageChanged: (pageIndex: Int, totalPages: Int) -> Unit = { _, _ -> },
    onTap: () -> Unit = {},
    onReady: () -> Unit = {}
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPageIndex)

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isScrolling by remember { mutableStateOf(false) }

    val totalPages = archive.pageCount
    val currentPageIndex = remember { mutableStateOf(initialPageIndex) }

    // Track scroll position changes with a single cancellable dismiss timer
    LaunchedEffect(listState, totalPages) {
        var dismissJob: Job? = null
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                currentPageIndex.value = index
                onPageChanged(index, totalPages)
                isScrolling = true
                dismissJob?.cancel()
                dismissJob = launch {
                    delay(2000)
                    isScrolling = false
                }
            }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
    ) {
        val maxWidthPx = constraints.maxWidth.toFloat()
        val maxHeightPx = constraints.maxHeight.toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                }
                .pointerInput(maxWidthPx, maxHeightPx) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 4f)
                        scale = newScale

                        if (newScale > 1f) {
                            val maxOffsetX = (maxWidthPx * (newScale - 1f)) / 2f
                            val maxOffsetY = (maxHeightPx * (newScale - 1f)) / 2f
                            val newOffsetX = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                            val newOffsetY = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                            offset = Offset(newOffsetX, newOffsetY)
                        } else {
                            offset = Offset.Zero
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = (scale <= 1.05f) // Disable list scroll while zoomed to allow panning
            ) {
                items(
                    count = totalPages,
                    key = { index -> archive.pages[index].entryName }
                ) { index ->
                    CbzPageItem(
                        archive = archive,
                        index = index,
                        isInitialPage = index == initialPageIndex,
                        onFirstPageReady = onReady
                    )
                }
            }
        }

        // Discrete Floating Page Indicator
        AnimatedVisibility(
            visible = isScrolling && totalPages > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                tonalElevation = 4.dp
            ) {
                Text(
                    text = "${currentPageIndex.value + 1} / $totalPages",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun CbzPageItem(
    archive: CbzArchive,
    index: Int,
    isInitialPage: Boolean = false,
    onFirstPageReady: () -> Unit = {}
) {
    val context = LocalContext.current

    // Extract file on background IO thread on demand
    val pageFile by produceState<File?>(initialValue = null, archive, index) {
        value = withContext(Dispatchers.IO) {
            runCatching { archive.getPageFile(index) }.getOrNull()
        }
    }

    // Trang mở đầu tiên có mặt là mốc "nội dung đã sẵn sàng" của cả màn đọc CBZ.
    LaunchedEffect(pageFile, isInitialPage) {
        if (isInitialPage && pageFile != null) {
            onFirstPageReady()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        val file = pageFile
        if (file != null && file.exists()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    .crossfade(true)
                    .build(),
                contentDescription = "Trang ${index + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            )
        } else {
            // Loading placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center
            ) {
                // Trang truyện đang giải nén: chờ ngắn, chưa biết tiến trình -> M3 loading indicator.
                // Dùng bản có container vì chỉ báo nằm trên nội dung khác (khung trang truyện), đúng
                // luật của spec: đặt trên nội dung thì cần container để đủ tương phản.
                ExpressiveContainedLoadingIndicator(modifier = Modifier.size(40.dp))
            }
        }
    }
}

package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.library.components.MangaListItem
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.presentation.core.util.plus

@Composable
fun BrowseSourceList(
    mangaList: LazyPagingItems<StateFlow<Manga>>,
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
    selectionMode: Boolean = false,
    selection: Set<Manga> = emptySet(),
    translateTitles: Boolean = false,
    translatedTitles: Map<Long, String> = emptyMap(),
    onMangaVisible: (Manga) -> Unit = {},
    titleMaxLines: Int = 2,
    skipCoverLoading: Boolean = false,
) {
    LazyColumn(
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
    ) {
        item {
            if (mangaList.loadState.prepend is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }

        items(count = mangaList.itemCount) { index ->
            val manga by mangaList[index]?.collectAsState() ?: return@items
            val isSelected = selectionMode && manga in selection
            val displayTitle = if (translateTitles) {
                translatedTitles[manga.id] ?: manga.title
            } else {
                manga.title
            }

            if (translateTitles) {
                onMangaVisible(manga)
            }

            BrowseSourceListItem(
                manga = manga,
                displayTitle = displayTitle,
                onClick = { onMangaClick(manga) },
                onLongClick = { onMangaLongClick(manga) },
                isSelected = isSelected,
                titleMaxLines = titleMaxLines,
                skipCoverLoading = skipCoverLoading,
                position = when {
                    mangaList.itemCount == 1 -> BrowseItemPosition.Standalone
                    index == 0 -> BrowseItemPosition.First
                    index == mangaList.itemCount - 1 -> BrowseItemPosition.Last
                    else -> BrowseItemPosition.Middle
                },
            )
        }

        item {
            if (mangaList.loadState.refresh is LoadState.Loading || mangaList.loadState.append is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
private fun BrowseSourceListItem(
    manga: Manga,
    displayTitle: String = manga.title,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
    isSelected: Boolean = false,
    titleMaxLines: Int = 2,
    skipCoverLoading: Boolean = false,
    position: BrowseItemPosition = BrowseItemPosition.Standalone,
) {
    val shape = when (position) {
        BrowseItemPosition.Standalone -> RoundedCornerShape(24.dp)
        BrowseItemPosition.First -> RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 2.dp,
            bottomEnd = 2.dp,
        )
        BrowseItemPosition.Middle -> RoundedCornerShape(2.dp)
        BrowseItemPosition.Last -> RoundedCornerShape(
            topStart = 2.dp,
            topEnd = 2.dp,
            bottomStart = 24.dp,
            bottomEnd = 24.dp,
        )
    }
    MangaListItem(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceBright),
        minHeight = 72.dp,
        isSelected = isSelected,
        title = displayTitle,
        titleMaxLines = titleMaxLines,
        coverData = MangaCover(
            mangaId = manga.id,
            sourceId = manga.source,
            isMangaFavorite = manga.favorite,
            url = if (skipCoverLoading) "" else manga.thumbnailUrl,
            lastModified = manga.coverLastModified,
        ),
        coverAlpha = if (manga.favorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
        badge = {
            InLibraryBadge(enabled = manga.favorite)
        },
        onLongClick = onLongClick,
        onClick = onClick,
    )
}

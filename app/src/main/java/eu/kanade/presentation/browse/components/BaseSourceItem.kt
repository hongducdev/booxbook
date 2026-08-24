package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.model.Source

@Composable
fun BaseSourceItem(
    source: Source,
    modifier: Modifier = Modifier,
    showLanguageInContent: Boolean = true,
    onClickItem: () -> Unit = {},
    onLongClickItem: () -> Unit = {},
    icon: @Composable RowScope.(Source) -> Unit = defaultIcon,
    action: (@Composable RowScope.(Source) -> Unit)? = null,
    swipeActions: List<BrowseItemAction> = emptyList(),
    position: BrowseItemPosition = BrowseItemPosition.Standalone,
    content: (@Composable RowScope.(Source, String?) -> Unit)? = null,
) {
    val sourceLangString = LocaleHelper.getSourceDisplayName(source.lang, LocalContext.current).takeIf {
        showLanguageInContent
    }
    BaseBrowseItem(
        modifier = modifier,
        onClickItem = onClickItem,
        onLongClickItem = onLongClickItem,
        icon = { icon.invoke(this, source) },
        action = action?.let {
            { it.invoke(this, source) }
        },
        swipeActions = swipeActions,
        position = position,
        supportingContent = if (content == null && sourceLangString != null) {
            {
                Text(
                    text = sourceLangString,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            null
        },
        content = {
            if (content != null) {
                content.invoke(this, source, sourceLangString)
            } else {
                defaultHeadline(source)
            }
        },
    )
}

private val defaultIcon: @Composable RowScope.(Source) -> Unit = { source ->
    SourceIcon(source = source)
}

@Composable
private fun RowScope.defaultHeadline(source: Source) {
    Text(
        text = source.name,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.weight(1f, fill = false),
    )
    SourceTypeBadge(source = source)
}

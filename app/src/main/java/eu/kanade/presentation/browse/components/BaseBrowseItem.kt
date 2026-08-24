package eu.kanade.presentation.browse.components

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class BrowseItemAction(
    val label: String,
    val icon: ImageVector,
    val background: Color,
    val onClick: () -> Unit,
)

enum class BrowseItemPosition {
    Standalone,
    First,
    Middle,
    Last,
}

@Composable
fun BaseBrowseItem(
    modifier: Modifier = Modifier,
    onClickItem: () -> Unit = {},
    onLongClickItem: () -> Unit = {},
    icon: (@Composable RowScope.() -> Unit)? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    swipeActions: List<BrowseItemAction> = emptyList(),
    supportingContent: (@Composable () -> Unit)? = null,
    position: BrowseItemPosition = BrowseItemPosition.Standalone,
    content: @Composable RowScope.() -> Unit = {},
) {
    val shape = browseItemShape(position)
    if (swipeActions.isEmpty()) {
        BrowseListItem(
            modifier = modifier.padding(
                horizontal = BrowseItemHorizontalInset,
                vertical = BrowseItemVerticalInset,
            ),
            shape = shape,
            onClickItem = onClickItem,
            onLongClickItem = onLongClickItem,
            icon = icon,
            action = action,
            supportingContent = supportingContent,
            content = content,
        )
        return
    }

    SwipeToRevealBrowseItem(
        modifier = modifier,
        actions = swipeActions,
        onClickItem = onClickItem,
        onLongClickItem = onLongClickItem,
        icon = icon,
        supportingContent = supportingContent,
        shape = shape,
        content = content,
    )
}

@Composable
private fun SwipeToRevealBrowseItem(
    actions: List<BrowseItemAction>,
    onClickItem: () -> Unit,
    onLongClickItem: () -> Unit,
    icon: (@Composable RowScope.() -> Unit)?,
    supportingContent: (@Composable () -> Unit)?,
    shape: Shape,
    content: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberSaveable(saver = AnchoredDraggableState.Saver()) {
        AnchoredDraggableState(initialValue = BrowseItemRevealState.Closed)
    }
    val actionWidth = 64.dp
    val actionSpacing = 2.dp
    val revealWidth = actionWidth * actions.size + actionSpacing * (actions.size - 1)
    val revealOffset = with(LocalDensity.current) {
        -revealWidth.toPx()
    }
    val animationSpec = spring<Float>()
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
        state = state,
        positionalThreshold = { distance -> distance * 0.35f },
        animationSpec = animationSpec,
    )
    val scope = rememberCoroutineScope()
    val closeActions = { scope.launch { state.animateTo(BrowseItemRevealState.Closed, animationSpec) } }

    LaunchedEffect(state, revealOffset) {
        state.updateAnchors(
            DraggableAnchors {
                BrowseItemRevealState.Closed at 0f
                BrowseItemRevealState.Revealed at revealOffset
            },
        )
    }

    Box(
        modifier = modifier
            .padding(horizontal = BrowseItemHorizontalInset, vertical = BrowseItemVerticalInset)
            .anchoredDraggable(
                state = state,
                orientation = Orientation.Horizontal,
                flingBehavior = flingBehavior,
            ),
    ) {
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.spacedBy(actionSpacing, Alignment.End),
        ) {
            actions.asReversed().forEach { itemAction ->
                Box(
                    modifier = Modifier
                        .width(actionWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(itemAction.background)
                        .clickable(
                            role = Role.Button,
                            onClick = {
                                itemAction.onClick()
                                closeActions()
                            },
                        )
                        .clearAndSetSemantics {},
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = itemAction.icon,
                        contentDescription = itemAction.label,
                        tint = contentColorFor(itemAction.background),
                    )
                }
            }
        }

        BrowseListItem(
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(
                        x = state.offset.takeIf(Float::isFinite)?.roundToInt() ?: 0,
                        y = 0,
                    )
                },
            onClickItem = {
                if (state.settledValue == BrowseItemRevealState.Revealed) {
                    closeActions()
                } else {
                    onClickItem()
                }
            },
            onLongClickItem = onLongClickItem,
            icon = icon,
            supportingContent = supportingContent,
            shape = shape,
            customActions = actions.map { itemAction ->
                itemAction.copy(
                    onClick = {
                        itemAction.onClick()
                        closeActions()
                    },
                )
            },
            content = content,
        )
    }
}

@Composable
private fun BrowseListItem(
    onClickItem: () -> Unit,
    onLongClickItem: () -> Unit,
    icon: (@Composable RowScope.() -> Unit)?,
    supportingContent: (@Composable () -> Unit)?,
    content: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable RowScope.() -> Unit)? = null,
    shape: Shape,
    customActions: List<BrowseItemAction> = emptyList(),
) {
    ListItem(
        onClick = onClickItem,
        onLongClick = onLongClickItem,
        modifier = modifier
            .semantics {
                this.customActions = customActions.map { itemAction ->
                    CustomAccessibilityAction(itemAction.label) {
                        itemAction.onClick()
                        true
                    }
                }
            },
        leadingContent = icon?.let {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    it()
                }
            }
        },
        supportingContent = supportingContent,
        trailingContent = action?.let {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    it()
                }
            }
        },
        shapes = ListItemDefaults.shapes(shape = shape),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun browseItemShape(position: BrowseItemPosition): Shape {
    val outerCorner = 24.dp
    val innerCorner = 2.dp
    return when (position) {
        BrowseItemPosition.Standalone -> RoundedCornerShape(outerCorner)
        BrowseItemPosition.First -> RoundedCornerShape(
            topStart = outerCorner,
            topEnd = outerCorner,
            bottomStart = innerCorner,
            bottomEnd = innerCorner,
        )
        BrowseItemPosition.Middle -> RoundedCornerShape(innerCorner)
        BrowseItemPosition.Last -> RoundedCornerShape(
            topStart = innerCorner,
            topEnd = innerCorner,
            bottomStart = outerCorner,
            bottomEnd = outerCorner,
        )
    }
}

private val BrowseItemHorizontalInset = 16.dp
private val BrowseItemVerticalInset = 1.dp

private enum class BrowseItemRevealState {
    Closed,
    Revealed,
}

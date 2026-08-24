package eu.kanade.presentation.more.settings.widget

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.LocalPreferenceHighlighted
import eu.kanade.presentation.more.settings.LocalPreferenceItemPosition
import eu.kanade.presentation.more.settings.LocalPreferenceMinHeight
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

enum class PreferenceItemPosition {
    Standalone,
    First,
    Middle,
    Last,
}

@Composable
internal fun BasePreferenceWidget(
    modifier: Modifier = Modifier,
    title: String? = null,
    subcomponent: @Composable (ColumnScope.() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    widget: @Composable (() -> Unit)? = null,
    position: PreferenceItemPosition? = null,
) {
    val highlighted = LocalPreferenceHighlighted.current
    val itemPosition = position ?: LocalPreferenceItemPosition.current
    val minHeight = LocalPreferenceMinHeight.current
    if (title.isNullOrBlank()) {
        CustomPreferenceContent(
            modifier = modifier,
            highlighted = highlighted,
            minHeight = minHeight,
            subcomponent = subcomponent,
            icon = icon,
            onClick = onClick,
            widget = widget,
        )
        return
    }

    val containerColor = preferenceContainerColor(
        highlighted = highlighted,
        baseColor = MaterialTheme.colorScheme.surfaceBright,
    )
    val essentialStyle = itemPosition != null
    val itemModifier = modifier
        .padding(
            horizontal = if (essentialStyle) EssentialItemHorizontalInset else PreferenceItemHorizontalInset,
            vertical = if (essentialStyle) EssentialItemVerticalInset else PreferenceItemVerticalInset,
        )
        .sizeIn(minHeight = minHeight)
        .fillMaxWidth()
    val leadingContent: (@Composable () -> Unit)? = if (icon != null) {
        {
            Box(contentAlignment = Alignment.Center) {
                icon()
            }
        }
    } else {
        null
    }
    val supportingContent: (@Composable () -> Unit)? = if (subcomponent != null) {
        {
            Column {
                subcomponent()
            }
        }
    } else {
        null
    }
    val trailingContent: (@Composable () -> Unit)? = if (widget != null) {
        {
            Box(contentAlignment = Alignment.Center) {
                widget()
            }
        }
    } else {
        null
    }
    val content: @Composable () -> Unit = {
        Text(
            text = title,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
            style = if (essentialStyle) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
        )
    }
    val colors = ListItemDefaults.colors(containerColor = containerColor)
    val shapes = ListItemDefaults.shapes(
        shape = itemPosition?.let(::essentialItemShape) ?: MaterialTheme.shapes.extraSmall,
    )
    val contentPadding = PaddingValues(
        horizontal = PrefsHorizontalPadding,
        vertical = 8.dp,
    )

    if (onClick != null) {
        ListItem(
            onClick = onClick,
            modifier = itemModifier,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            supportingContent = supportingContent,
            shapes = shapes,
            colors = colors,
            contentPadding = contentPadding,
            content = content,
        )
    } else {
        ListItem(
            modifier = itemModifier,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            supportingContent = supportingContent,
            shapes = shapes,
            colors = colors,
            contentPadding = contentPadding,
            content = content,
        )
    }
}

@Composable
private fun CustomPreferenceContent(
    highlighted: Boolean,
    minHeight: androidx.compose.ui.unit.Dp,
    subcomponent: @Composable (ColumnScope.() -> Unit)?,
    icon: @Composable (() -> Unit)?,
    onClick: (() -> Unit)?,
    widget: @Composable (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .highlightBackground(highlighted)
            .sizeIn(minHeight = minHeight)
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.padding(start = PrefsHorizontalPadding, end = 8.dp),
                content = { icon() },
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = PrefsVerticalPadding),
        ) {
            subcomponent?.invoke(this)
        }
        if (widget != null) {
            Box(
                modifier = Modifier.padding(end = PrefsHorizontalPadding),
                content = { widget() },
            )
        }
    }
}

@Composable
internal fun Modifier.highlightBackground(highlighted: Boolean): Modifier {
    return background(preferenceContainerColor(highlighted, Color.Transparent))
}

@Composable
private fun preferenceContainerColor(
    highlighted: Boolean,
    baseColor: Color,
): Color {
    var highlightFlag by remember { mutableStateOf(false) }
    LaunchedEffect(highlighted) {
        if (highlighted) {
            highlightFlag = true
            delay(3.seconds)
            highlightFlag = false
        }
    }
    val highlightColor = MaterialTheme.colorScheme.surfaceTint
        .copy(alpha = .12f)
        .compositeOver(baseColor)
    val color by animateColorAsState(
        targetValue = if (highlightFlag) highlightColor else baseColor,
        animationSpec = if (highlightFlag) {
            repeatable(
                iterations = 5,
                animation = tween(durationMillis = 200),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(
                    offsetMillis = 600,
                    offsetType = StartOffsetType.Delay,
                ),
            )
        } else {
            tween(200)
        },
        label = "highlight",
    )
    return color
}

internal fun essentialItemShape(position: PreferenceItemPosition): Shape {
    val outerCorner = 24.dp
    val innerCorner = 2.dp
    return when (position) {
        PreferenceItemPosition.Standalone -> RoundedCornerShape(outerCorner)
        PreferenceItemPosition.First -> RoundedCornerShape(
            topStart = outerCorner,
            topEnd = outerCorner,
            bottomStart = innerCorner,
            bottomEnd = innerCorner,
        )
        PreferenceItemPosition.Middle -> RoundedCornerShape(innerCorner)
        PreferenceItemPosition.Last -> RoundedCornerShape(
            topStart = innerCorner,
            topEnd = innerCorner,
            bottomStart = outerCorner,
            bottomEnd = outerCorner,
        )
    }
}

internal val TrailingWidgetBuffer = 16.dp
internal val EssentialItemHorizontalInset = 16.dp
internal val EssentialItemVerticalInset = 1.dp
internal val PreferenceItemHorizontalInset = 8.dp
internal val PreferenceItemVerticalInset = 2.dp
internal val PrefsHorizontalPadding = 16.dp
internal val PrefsVerticalPadding = 16.dp

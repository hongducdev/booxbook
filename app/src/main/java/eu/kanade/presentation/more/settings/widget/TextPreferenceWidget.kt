package eu.kanade.presentation.more.settings.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.CatppuccinColor
import eu.kanade.presentation.more.settings.LocalPreferenceItemPosition
import eu.kanade.presentation.theme.TachiyomiPreviewTheme

@Composable
fun TextPreferenceWidget(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    widget: @Composable (() -> Unit)? = null,
    onPreferenceClick: (() -> Unit)? = null,
    position: PreferenceItemPosition? = null,
    catppuccinColor: CatppuccinColor? = null,
) {
    val essentialStyle = position != null || LocalPreferenceItemPosition.current != null
    BasePreferenceWidget(
        modifier = modifier,
        title = title,
        subcomponent = if (!subtitle.isNullOrBlank()) {
            {
                Text(
                    text = subtitle,
                    style = if (essentialStyle) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    maxLines = 10,
                )
            }
        } else {
            null
        },
        icon = if (icon != null) {
            {
                if (position != null) {
                    val (containerColor, contentColor) = catppuccinColorsFor(
                        key = title.orEmpty(),
                        preferredColor = catppuccinColor,
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(containerColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            tint = contentColor,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                } else {
                    Icon(
                        imageVector = icon,
                        tint = iconTint,
                        contentDescription = null,
                    )
                }
            }
        } else {
            null
        },
        onClick = onPreferenceClick,
        widget = widget,
        position = position,
    )
}

private fun catppuccinColorsFor(
    key: String,
    preferredColor: CatppuccinColor?,
): Pair<Color, Color> {
    val paletteColor = preferredColor
        ?: CatppuccinColor.entries[(key.hashCode() and Int.MAX_VALUE) % CatppuccinColor.entries.size]
    return Color(paletteColor.mocha) to Color(paletteColor.latte)
}

@PreviewLightDark
@Composable
private fun TextPreferenceWidgetPreview() {
    TachiyomiPreviewTheme {
        Surface {
            Column {
                TextPreferenceWidget(
                    title = "Text preference with icon",
                    subtitle = "Text preference summary",
                    icon = Icons.Filled.Preview,
                    onPreferenceClick = {},
                )
                TextPreferenceWidget(
                    title = "Text preference",
                    subtitle = "Text preference summary",
                    onPreferenceClick = {},
                )
            }
        }
    }
}

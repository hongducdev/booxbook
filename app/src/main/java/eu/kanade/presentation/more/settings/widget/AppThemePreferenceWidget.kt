package eu.kanade.presentation.more.settings.widget

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType

@Composable
internal fun AppThemePreviewWidget(
    value: AppTheme,
    amoled: Boolean,
) {
    BasePreferenceWidget(
        subcomponent = {
            Box(
                modifier = Modifier
                    .width(148.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = MaterialTheme.padding.small),
            ) {
                TachiyomiTheme(appTheme = value, amoled = amoled) {
                    AppThemePreviewItem(
                        selected = true,
                        onClick = null,
                        showCheck = false,
                    )
                }
            }
        },
    )
}

@Composable
internal fun AppThemePreferenceWidget(
    value: AppTheme,
    amoled: Boolean,
    onItemClick: (AppTheme) -> Unit,
    expanded: Boolean = false,
) {
    val context = LocalContext.current
    val appThemes = remember {
        AppTheme.entries.filterNot {
            it.titleRes == null || (it == AppTheme.MONET && !DeviceUtil.isDynamicColorAvailable)
        }
    }
    val selectTheme: (AppTheme) -> Unit = {
        onItemClick(it)
        (context as? Activity)?.let(ActivityCompat::recreate)
    }

    BasePreferenceWidget(
        subcomponent = {
            if (expanded) {
                ExpandedAppThemesList(value, amoled, appThemes, selectTheme)
            } else {
                AppThemesList(value, amoled, appThemes, selectTheme)
            }
        },
    )
}

@Composable
private fun ExpandedAppThemesList(
    currentTheme: AppTheme,
    amoled: Boolean,
    appThemes: List<AppTheme>,
    onItemClick: (AppTheme) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LazyRow(
            modifier = Modifier.selectableGroup(),
            contentPadding = PaddingValues(horizontal = PrefsHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            items(items = appThemes, key = { it.name }) { appTheme ->
                val selected = currentTheme == appTheme
                Column(
                    modifier = Modifier
                        .width(104.dp)
                        .selectable(
                            selected = selected,
                            onClick = { onItemClick(appTheme) },
                            role = Role.RadioButton,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TachiyomiTheme(appTheme = appTheme, amoled = amoled) {
                        ThemePalettePreview(
                            appTheme = appTheme,
                            selected = selected,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(appTheme.titleRes!!),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        minLines = 2,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemePalettePreview(
    appTheme: AppTheme,
    selected: Boolean,
) {
    val shape = RoundedCornerShape(22.dp)
    val (containerColor, leftColor, rightColor) = themePaletteColors(appTheme, MaterialTheme.colorScheme)
    Box(
        modifier = Modifier
            .size(88.dp)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .padding(5.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(leftColor),
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(rightColor),
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(MR.strings.selected),
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

internal fun themePaletteColors(
    appTheme: AppTheme,
    colorScheme: ColorScheme,
): Triple<Color, Color, Color> {
    return if (appTheme == AppTheme.CATPPUCCIN) {
        Triple(colorScheme.surfaceContainerHigh, colorScheme.background, colorScheme.scrim)
    } else {
        Triple(colorScheme.surfaceContainerHigh, colorScheme.tertiaryContainer, colorScheme.secondaryContainer)
    }
}

@Composable
private fun AppThemesList(
    currentTheme: AppTheme,
    amoled: Boolean,
    appThemes: List<AppTheme>,
    onItemClick: (AppTheme) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = PrefsHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(items = appThemes, key = { it.name }) { appTheme ->
            Column(
                modifier = Modifier
                    .width(114.dp)
                    .padding(top = 8.dp),
            ) {
                TachiyomiTheme(appTheme = appTheme, amoled = amoled) {
                    AppThemePreviewItem(
                        selected = currentTheme == appTheme,
                        onClick = { onItemClick(appTheme) },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(appTheme.titleRes!!),
                    modifier = Modifier
                        .fillMaxWidth()
                        .secondaryItemAlpha(),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    minLines = 2,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun AppThemePreviewItem(
    selected: Boolean,
    onClick: (() -> Unit)?,
    showCheck: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .border(
                width = 4.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else DividerDefaults.color,
                shape = RoundedCornerShape(17.dp),
            )
            .padding(4.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.background)
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() }),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.8f)
                    .weight(0.7f)
                    .padding(end = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = MaterialTheme.shapes.small,
                    ),
            )

            Box(
                modifier = Modifier.weight(0.3f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (selected && showCheck) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(MR.strings.selected),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .padding(start = 8.dp, top = 2.dp)
                .background(
                    color = DividerDefaults.color,
                    shape = MaterialTheme.shapes.small,
                )
                .fillMaxWidth(0.5f)
                .aspectRatio(MangaCover.Book.ratio),
        ) {
            Row(
                modifier = Modifier
                    .padding(4.dp)
                    .size(width = 24.dp, height = 16.dp)
                    .clip(RoundedCornerShape(5.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(12.dp)
                        .background(MaterialTheme.colorScheme.tertiary),
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(12.dp)
                        .background(MaterialTheme.colorScheme.secondary),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(17.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .alpha(0.6f)
                            .height(17.dp)
                            .weight(1f)
                            .background(
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = MaterialTheme.shapes.small,
                            ),
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun AppThemesListPreview() {
    var appTheme by remember { mutableStateOf(AppTheme.DEFAULT) }
    Injekt.addSingleton(fullType<UiPreferences>(), UiPreferences(InMemoryPreferenceStore()))
    TachiyomiTheme(appTheme = appTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ExpandedAppThemesList(
                currentTheme = appTheme,
                amoled = false,
                appThemes = AppTheme.entries.filter { it.titleRes != null },
                onItemClick = { appTheme = it },
            )
        }
    }
}

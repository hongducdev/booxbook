package eu.kanade.presentation.more.settings.widget

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import eu.kanade.domain.ui.model.ThemeMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

private val options = mapOf(
    ThemeMode.SYSTEM to MR.strings.theme_system,
    ThemeMode.LIGHT to MR.strings.theme_light,
    ThemeMode.DARK to MR.strings.theme_dark,
)

@Composable
internal fun AppThemeModePreferenceWidget(
    value: ThemeMode,
    onItemClick: (ThemeMode) -> Unit,
    expanded: Boolean = false,
) {
    val localizedOptions = options.map { (mode, labelRes) ->
        mode to stringResource(labelRes)
    }
    val interactionSources = remember {
        List(options.size) { MutableInteractionSource() }
    }

    BasePreferenceWidget(
        subcomponent = {
            ButtonGroup(
                overflowIndicator = { menuState ->
                    ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PrefsHorizontalPadding)
                    .selectableGroup(),
                expandedRatio = 1f,
            ) {
                localizedOptions.forEachIndexed { index, (mode, label) ->
                    val selected = mode == value
                    customItem(
                        buttonGroupContent = {
                            ToggleButton(
                                checked = selected,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        onItemClick(mode)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .animateWidth(interactionSources[index])
                                    .semantics { role = Role.RadioButton },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    localizedOptions.lastIndex -> {
                                        ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    }
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                interactionSource = interactionSources[index],
                            ) {
                                if (expanded) {
                                    Icon(
                                        imageVector = when (mode) {
                                            ThemeMode.SYSTEM -> Icons.Outlined.SettingsBrightness
                                            ThemeMode.LIGHT -> Icons.Outlined.LightMode
                                            ThemeMode.DARK -> Icons.Outlined.DarkMode
                                        },
                                        contentDescription = null,
                                    )
                                }
                                Text(label)
                            }
                        },
                        menuContent = { menuState ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    if (!selected) {
                                        onItemClick(mode)
                                    }
                                    menuState.dismiss()
                                },
                                modifier = Modifier.semantics {
                                    role = Role.RadioButton
                                    this.selected = selected
                                },
                                trailingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                        )
                                    }
                                } else {
                                    null
                                },
                                interactionSource = interactionSources[index],
                            )
                        },
                    )
                }
            }
        },
    )
}

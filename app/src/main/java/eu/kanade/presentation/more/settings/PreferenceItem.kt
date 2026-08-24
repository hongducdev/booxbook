package eu.kanade.presentation.more.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.widget.EditTextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.EssentialItemHorizontalInset
import eu.kanade.presentation.more.settings.widget.EssentialItemVerticalInset
import eu.kanade.presentation.more.settings.widget.InfoWidget
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.MultiSelectListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.PreferenceItemPosition
import eu.kanade.presentation.more.settings.widget.PrefsHorizontalPadding
import eu.kanade.presentation.more.settings.widget.PrefsVerticalPadding
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.essentialItemShape
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.components.BaseSliderItem
import tachiyomi.presentation.core.util.collectAsState

val LocalPreferenceHighlighted = compositionLocalOf(structuralEqualityPolicy()) { false }
val LocalPreferenceItemPosition = compositionLocalOf<PreferenceItemPosition?> { null }
val LocalPreferenceMinHeight = compositionLocalOf(structuralEqualityPolicy()) { 56.dp }

@Composable
fun StatusWrapper(
    item: Preference.PreferenceItem<*, *>,
    highlightKey: String?,
    position: PreferenceItemPosition?,
    content: @Composable () -> Unit,
) {
    val enabled = item.enabled
    val highlighted = item.title == highlightKey
    AnimatedVisibility(
        visible = enabled,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        content = {
            CompositionLocalProvider(
                LocalPreferenceHighlighted provides highlighted,
                LocalPreferenceItemPosition provides position,
                content = content,
            )
        },
    )
}

@Composable
internal fun PreferenceItem(
    item: Preference.PreferenceItem<*, *>,
    highlightKey: String?,
    position: PreferenceItemPosition? = null,
) {
    val scope = rememberCoroutineScope()
    StatusWrapper(
        item = item,
        highlightKey = highlightKey,
        position = position,
    ) {
        when (item) {
            is Preference.PreferenceItem.SwitchPreference -> {
                val value by item.preference.collectAsState()
                SwitchPreferenceWidget(
                    title = item.title,
                    subtitle = item.subtitle,
                    icon = item.icon,
                    checked = value,
                    onCheckedChanged = { newValue ->
                        scope.launch {
                            if (item.onValueChanged(newValue)) {
                                item.preference.set(newValue)
                            }
                        }
                    },
                )
            }
            is Preference.PreferenceItem.SliderPreference -> {
                val itemPosition = LocalPreferenceItemPosition.current
                BaseSliderItem(
                    value = item.value,
                    valueRange = item.valueRange,
                    steps = item.steps,
                    title = item.title,
                    subtitle = item.subtitle,
                    valueString = item.valueString.takeUnless { it.isNullOrEmpty() } ?: item.value.toString(),
                    onChange = {
                        scope.launch {
                            item.onValueChanged(it)
                        }
                    },
                    titleStyle = MaterialTheme.typography.bodyMedium,
                    subtitleStyle = MaterialTheme.typography.labelMedium,
                    modifier = if (itemPosition != null) {
                        Modifier
                            .padding(
                                horizontal = EssentialItemHorizontalInset,
                                vertical = EssentialItemVerticalInset,
                            )
                            .background(MaterialTheme.colorScheme.surfaceBright, essentialItemShape(itemPosition))
                            .padding(horizontal = PrefsHorizontalPadding, vertical = 8.dp)
                    } else {
                        Modifier.padding(
                            horizontal = PrefsHorizontalPadding,
                            vertical = PrefsVerticalPadding,
                        )
                    },
                )
            }
            is Preference.PreferenceItem.ListPreference<*> -> {
                val value by item.preference.collectAsState()
                ListPreferenceWidget(
                    value = value,
                    title = item.title,
                    subtitle = item.internalSubtitleProvider(value, item.entries),
                    icon = item.icon,
                    entries = item.entries,
                    onValueChange = { newValue ->
                        scope.launch {
                            if (item.internalOnValueChanged(newValue!!)) {
                                item.internalSet(newValue)
                            }
                        }
                    },
                )
            }
            is Preference.PreferenceItem.BasicListPreference -> {
                ListPreferenceWidget(
                    value = item.value,
                    title = item.title,
                    subtitle = item.subtitleProvider(item.value, item.entries),
                    icon = item.icon,
                    entries = item.entries,
                    onValueChange = { scope.launch { item.onValueChanged(it) } },
                )
            }
            is Preference.PreferenceItem.MultiSelectListPreference<*> -> {
                val values by item.preference.collectAsState()
                MultiSelectListPreferenceWidget(
                    values = values,
                    title = item.title,
                    subtitle = item.internalSubtitleProvider(values, item.entries),
                    icon = item.icon,
                    entries = item.entries,
                    onValuesChange = { newValues ->
                        scope.launch {
                            if (item.internalOnValueChanged(newValues)) {
                                item.internalSet(newValues)
                            }
                        }
                    },
                )
            }
            is Preference.PreferenceItem.TextPreference -> {
                TextPreferenceWidget(
                    title = item.title,
                    subtitle = item.subtitle,
                    icon = item.icon,
                    widget = item.widget,
                    onPreferenceClick = item.onClick,
                )
            }
            is Preference.PreferenceItem.EditTextPreference -> {
                val values by item.preference.collectAsState()
                EditTextPreferenceWidget(
                    title = item.title,
                    subtitle = item.subtitle,
                    icon = item.icon,
                    value = values,
                    isPassword = item.isPassword,
                    onConfirm = {
                        val accepted = item.onValueChanged(it)
                        if (accepted) item.preference.set(it)
                        accepted
                    },
                )
            }
            is Preference.PreferenceItem.InfoPreference -> {
                InfoWidget(text = item.title)
            }
            is Preference.PreferenceItem.CustomPreference -> {
                item.content()
            }
        }
    }
}

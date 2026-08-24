package eu.kanade.tachiyomi.ui.setting

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.screen.SettingsAppearanceScreen
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.SettingsMainScreen
import eu.kanade.presentation.more.settings.screen.SettingsTranslationScreen
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import tachiyomi.presentation.core.components.TwoPanelBox

class SettingsScreen(
    private val destination: Int? = null,
    private val finishActivityOnExit: Boolean = false,
) : Screen() {

    constructor(destination: Destination, finishActivityOnExit: Boolean = false) :
        this(destination.id, finishActivityOnExit)

    @Composable
    override fun Content() {
        val parentNavigator = LocalNavigator.currentOrThrow
        val activity = LocalActivity.current
        SettingsScreenContent(
            destination = destination,
            onExit = {
                if (finishActivityOnExit) activity?.finish() else parentNavigator.pop()
            },
        )
    }

    sealed class Destination(internal val id: Int) {
        data object About : Destination(0)
        data object DataAndStorage : Destination(1)
        data object Translation : Destination(3)
    }
}

@Composable
internal fun SettingsScreenContent(
    destination: Int? = null,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (!isTabletUi()) {
            Navigator(
                screen = when (destination) {
                    SettingsScreen.Destination.About.id -> AboutScreen
                    SettingsScreen.Destination.DataAndStorage.id -> SettingsDataScreen
                    SettingsScreen.Destination.Translation.id -> SettingsTranslationScreen
                    else -> SettingsMainScreen
                },
                onBackPressed = null,
            ) {
                val pop: () -> Unit = { if (it.canPop) it.pop() else onExit() }
                BackHandler(onBack = pop)
                CompositionLocalProvider(LocalBackPress provides pop) {
                    DefaultNavigatorScreenTransition(navigator = it)
                }
            }
        } else {
            Navigator(
                screen = when (destination) {
                    SettingsScreen.Destination.About.id -> AboutScreen
                    SettingsScreen.Destination.DataAndStorage.id -> SettingsDataScreen
                    SettingsScreen.Destination.Translation.id -> SettingsTranslationScreen
                    else -> SettingsAppearanceScreen
                },
                onBackPressed = null,
            ) {
                val pop: () -> Unit = { if (it.canPop) it.pop() else onExit() }
                BackHandler(onBack = pop)
                val insets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                TwoPanelBox(
                    modifier = Modifier
                        .windowInsetsPadding(insets)
                        .consumeWindowInsets(insets),
                    startContent = {
                        CompositionLocalProvider(LocalBackPress provides onExit) {
                            SettingsMainScreen.Content(twoPane = true)
                        }
                    },
                    endContent = {
                        CompositionLocalProvider(LocalBackPress provides pop) {
                            DefaultNavigatorScreenTransition(navigator = it)
                        }
                    },
                )
            }
        }
    }
}

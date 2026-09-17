package com.booxbook.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val ExpressiveLightColorScheme = lightColorScheme(
    primary = ExpressivePrimaryLight,
    onPrimary = ExpressiveOnPrimaryLight,
    primaryContainer = ExpressivePrimaryContainerLight,
    onPrimaryContainer = ExpressiveOnPrimaryContainerLight,
    secondary = ExpressiveSecondaryLight,
    onSecondary = ExpressiveOnSecondaryLight,
    secondaryContainer = ExpressiveSecondaryContainerLight,
    onSecondaryContainer = ExpressiveOnSecondaryContainerLight,
    tertiary = ExpressiveTertiaryLight,
    onTertiary = ExpressiveOnTertiaryLight,
    tertiaryContainer = ExpressiveTertiaryContainerLight,
    onTertiaryContainer = ExpressiveOnTertiaryContainerLight,
    background = ExpressiveBackgroundLight,
    onBackground = ExpressiveOnBackgroundLight,
    surface = ExpressiveSurfaceLight,
    onSurface = ExpressiveOnSurfaceLight,
    surfaceVariant = ExpressiveSurfaceVariantLight,
    onSurfaceVariant = ExpressiveOnSurfaceVariantLight,
    surfaceContainer = ExpressiveSurfaceContainerLight,
    surfaceContainerLow = ExpressiveSurfaceContainerLowLight,
    surfaceContainerLowest = ExpressiveSurfaceContainerLowestLight,
    surfaceContainerHigh = ExpressiveSurfaceContainerHighLight,
    surfaceContainerHighest = ExpressiveSurfaceContainerHighestLight
)

val ExpressiveDarkColorScheme = darkColorScheme(
    primary = ExpressivePrimaryDark,
    onPrimary = ExpressiveOnPrimaryDark,
    primaryContainer = ExpressivePrimaryContainerDark,
    onPrimaryContainer = ExpressiveOnPrimaryContainerDark,
    secondary = ExpressiveSecondaryDark,
    onSecondary = ExpressiveOnSecondaryDark,
    secondaryContainer = ExpressiveSecondaryContainerDark,
    onSecondaryContainer = ExpressiveOnSecondaryContainerDark,
    tertiary = ExpressiveTertiaryDark,
    onTertiary = ExpressiveOnTertiaryDark,
    tertiaryContainer = ExpressiveTertiaryContainerDark,
    onTertiaryContainer = ExpressiveOnTertiaryContainerDark,
    background = ExpressiveBackgroundDark,
    onBackground = ExpressiveOnBackgroundDark,
    surface = ExpressiveSurfaceDark,
    onSurface = ExpressiveOnSurfaceDark,
    surfaceVariant = ExpressiveSurfaceVariantDark,
    onSurfaceVariant = ExpressiveOnSurfaceVariantDark,
    surfaceContainer = ExpressiveSurfaceContainerDark,
    surfaceContainerLow = ExpressiveSurfaceContainerLowDark,
    surfaceContainerLowest = ExpressiveSurfaceContainerLowestDark,
    surfaceContainerHigh = ExpressiveSurfaceContainerHighDark,
    surfaceContainerHighest = ExpressiveSurfaceContainerHighestDark
)

enum class ReaderThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    SEPIA,
    AMOLED
}

/**
 * Material 3 Expressive Application Theme matching JustForPixel-ExpressiveLab.
 * Features Outfit typography, expressive purple color schemes, and AMOLED pure pitch black support.
 */
@Composable
fun BooxBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // false by default to showcase the vibrant ExpressiveLab palette
    oledBlack: Boolean = false,
    readerThemeMode: ReaderThemeMode = ReaderThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val baseColorScheme: ColorScheme = when (readerThemeMode) {
        ReaderThemeMode.SEPIA -> lightColorScheme(
            primary = SepiaPrimary,
            background = SepiaBackground,
            onBackground = SepiaOnBackground,
            surface = SepiaSurface,
            onSurface = SepiaOnBackground
        )
        ReaderThemeMode.AMOLED -> darkColorScheme(
            primary = AmoledPrimary,
            background = AmoledBackground,
            onBackground = AmoledOnBackground,
            surface = AmoledSurface,
            onSurface = AmoledOnBackground,
            surfaceContainer = AmoledSurfaceContainer,
            surfaceContainerLow = AmoledSurfaceContainerLow,
            surfaceContainerHigh = AmoledSurfaceContainerHigh,
            surfaceContainerHighest = AmoledSurfaceContainerHighest
        )
        ReaderThemeMode.LIGHT -> ExpressiveLightColorScheme
        ReaderThemeMode.DARK -> ExpressiveDarkColorScheme
        ReaderThemeMode.SYSTEM -> {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) ExpressiveDarkColorScheme else ExpressiveLightColorScheme
            }
        }
    }

    // OLED Pitch Black AMOLED support matching ExpressiveLab
    val finalColorScheme = if (darkTheme && oledBlack) {
        baseColorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color(0xFF101010),
            surfaceContainerLow = Color(0xFF080808),
            surfaceContainerLowest = Color.Black,
            surfaceContainerHigh = Color(0xFF181818),
            surfaceContainerHighest = Color(0xFF222222)
        )
    } else {
        baseColorScheme
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = ExpressiveTypography,
        shapes = ExpressiveShapes,
        content = content
    )
}

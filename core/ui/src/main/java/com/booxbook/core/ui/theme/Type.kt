package com.booxbook.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.booxbook.core.ui.R

/**
 * Google Sans Flex 400 (Regular with full Roundness 'ROND' 100).
 */
@OptIn(ExperimentalTextApi::class)
val GoogleSansFlex400 = FontFamily(
    Font(
        resId = R.font.gflex_variable,
        weight = FontWeight.W400,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.Setting("ROND", 100f)
        )
    )
)

/**
 * Google Sans Flex 600 (SemiBold with full Roundness 'ROND' 100).
 */
@OptIn(ExperimentalTextApi::class)
val GoogleSansFlex600 = FontFamily(
    Font(
        resId = R.font.gflex_variable,
        weight = FontWeight.W600,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(600),
            FontVariation.Setting("ROND", 100f)
        )
    )
)

/**
 * Google Sans Flex Display (Bold with full Roundness 'ROND' 100 for Expressive Headlines).
 */
@OptIn(ExperimentalTextApi::class)
val GoogleSansFlexDisplay = FontFamily(
    Font(
        resId = R.font.gflex_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(750),
            FontVariation.Setting("ROND", 100f),
            FontVariation.Setting("opsz", 72f)
        )
    )
)

/**
 * Full multi-weight Google Sans Flex family with 100% Roundness ('ROND' 100).
 */
@OptIn(ExperimentalTextApi::class)
val GoogleSansFlex = FontFamily(
    Font(
        resId = R.font.gflex_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.Setting("ROND", 100f)
        )
    ),
    Font(
        resId = R.font.gflex_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.Setting("ROND", 100f)
        )
    ),
    Font(
        resId = R.font.gflex_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(600),
            FontVariation.Setting("ROND", 100f)
        )
    ),
    Font(
        resId = R.font.gflex_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(750),
            FontVariation.Setting("ROND", 100f)
        )
    )
)

/**
 * Expressive typography hierarchy using Google Sans Flex rounded variable font.
 */
val ExpressiveTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = GoogleSansFlexDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp
    ),
    displayMedium = TextStyle(
        fontFamily = GoogleSansFlexDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontFamily = GoogleSansFlexDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = GoogleSansFlexDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = GoogleSansFlexDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = GoogleSansFlex600,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = GoogleSansFlex600,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = GoogleSansFlex600,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = GoogleSansFlex600,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = GoogleSansFlex400,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = GoogleSansFlex400,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = GoogleSansFlex400,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = GoogleSansFlex600,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = GoogleSansFlex600,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = GoogleSansFlex400,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * The app theme: Miuix colors and text styles, with a Material 3 color scheme derived from them so
 * Material components placed inside Miuix screens keep the same look.
 */
@Composable
fun AndroidRemoteControlMcpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val controller =
        remember(darkTheme) {
            ThemeController(colorSchemeMode = ColorSchemeMode.System, isDark = darkTheme)
        }

    MiuixTheme(controller = controller) {
        val miuixColors = MiuixTheme.colorScheme
        val materialColors = remember(miuixColors, darkTheme) { miuixColors.toMaterialColorScheme(darkTheme) }
        MaterialTheme(
            colorScheme = materialColors,
            typography = AppTypography,
        ) {
            CompositionLocalProvider(LocalContentColor provides miuixColors.onBackground, content = content)
        }
    }
}

/** The Material 3 color scheme that matches these Miuix colors. */
private fun Colors.toMaterialColorScheme(darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = surface,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariantSummary,
        surfaceContainerLowest = surfaceContainer,
        surfaceContainerLow = surfaceContainer,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = dividerLine,
    )
}

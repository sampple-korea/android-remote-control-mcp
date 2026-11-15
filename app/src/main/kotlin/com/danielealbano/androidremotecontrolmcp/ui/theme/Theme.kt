@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * The app theme: the Miuix design language (HyperOS look), following the system light/dark setting.
 */
@Composable
fun AndroidRemoteControlMcpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val controller =
        remember(darkTheme) {
            ThemeController(
                colorSchemeMode = if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
                isDark = darkTheme,
            )
        }
    MiuixTheme(controller = controller, content = content)
}

/** Whether the current Miuix color scheme is the dark one. */
@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean = MiuixTheme.colorSchemeMode == ColorSchemeMode.Dark

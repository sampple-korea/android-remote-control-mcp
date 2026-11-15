// Adapted from KernelSU (https://github.com/tiann/KernelSU, GPL-3.0).

@file:Suppress("FunctionNaming", "MagicNumber")

package com.danielealbano.androidremotecontrolmcp.ui.components.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A backdrop recording the scrolling content (over the surface color), for the frosted bars drawn
 * above it. Null when the device cannot blur, in which case the bars are drawn opaque.
 */
@Composable
fun rememberBlurBackdrop(): LayerBackdrop? {
    if (!isRenderEffectSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/** The color a bar drawn over [backdrop] uses: transparent when frosted, the surface otherwise. */
@Composable
fun blurredBarColor(backdrop: LayerBackdrop?): Color =
    if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface

/** Frosts [content] (a top bar) with a blur of whatever scrolls beneath it in [backdrop]. */
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            if (backdrop != null) {
                Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = RectangleShape,
                    blurRadius = 25f,
                    colors =
                        BlurColors(
                            blendColors = listOf(BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.87f))),
                        ),
                )
            } else {
                Modifier
            },
    ) {
        content()
    }
}

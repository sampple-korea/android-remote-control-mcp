// Adapted from KernelSU (https://github.com/tiann/KernelSU, GPL-3.0).

@file:Suppress("MagicNumber")

package com.danielealbano.androidremotecontrolmcp.ui.components.glass

import android.graphics.RuntimeShader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The soft glow that follows the finger across the liquid glass bar while it is pressed. */
internal class InteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
) {
    private val pressProgressAnimationSpec = spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero

    private val shader = RuntimeShader(HIGHLIGHT_SHADER)

    val modifier: Modifier =
        Modifier.drawWithContent {
            val progress = pressProgressAnimation.value
            if (progress > 0f) {
                drawRect(Color.White.copy(0.06f * progress), blendMode = BlendMode.Plus)
                val highlightPosition = position(size, positionAnimation.value)
                shader.setFloatUniform("size", size.width, size.height)
                shader.setColorUniform("color", Color.White.copy(0.12f * progress).toArgb())
                shader.setFloatUniform("radius", size.minDimension * 1.2f)
                shader.setFloatUniform(
                    "position",
                    highlightPosition.x.fastCoerceIn(0f, size.width),
                    highlightPosition.y.fastCoerceIn(0f, size.height),
                )
                drawRect(ShaderBrush(shader), blendMode = BlendMode.Plus)
            }
            drawContent()
        }

    val gestureModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            inspectDragGestures(
                onDragStart = { down ->
                    startPosition = down.position
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                        launch { positionAnimation.snapTo(startPosition) }
                    }
                },
                onDragEnd = { settle() },
                onDragCancel = { settle() },
            ) { change, _ ->
                animationScope.launch { positionAnimation.snapTo(change.position) }
            }
        }

    private fun settle() {
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
        }
    }

    private companion object {
        const val HIGHLIGHT_SHADER = """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
    }
}

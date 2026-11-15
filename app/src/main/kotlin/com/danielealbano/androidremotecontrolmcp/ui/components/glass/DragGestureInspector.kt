// Adapted from KernelSU (https://github.com/tiann/KernelSU, GPL-3.0).

package com.danielealbano.androidremotecontrolmcp.ui.components.glass

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.util.fastFirstOrNull

/**
 * Observes drag gestures on the [PointerEventPass.Initial] pass without consuming them, so the
 * children (the tab items) still receive their own clicks.
 */
internal suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

        onDragStart(down)
        onDrag(down, Offset.Zero)
        val upEvent =
            drag(
                pointerId = down.id,
                onDrag = { onDrag(it, it.positionChangeIgnoreConsumed()) },
            )
        if (upEvent != null) onDragEnd(upEvent) else onDragCancel()
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    var pointer = pointerId
    var upChange: PointerInputChange? = null
    var done = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    while (!done) {
        val change = awaitDragOrUp(pointer)
        when {
            change == null -> {
                done = true
            }

            change.changedToUpIgnoreConsumed() -> {
                upChange = change
                done = true
            }

            else -> {
                onDrag(change)
                pointer = change.id
            }
        }
    }
    return upChange
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(pointerId: PointerId): PointerInputChange? {
    var pointer = pointerId
    var result: PointerInputChange? = null
    var done = false
    while (!done) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer }
        when {
            dragEvent == null -> {
                done = true
            }

            dragEvent.changedToUpIgnoreConsumed() -> {
                val otherDown = event.changes.fastFirstOrNull { it.pressed }
                if (otherDown == null) {
                    result = dragEvent
                    done = true
                } else {
                    pointer = otherDown.id
                }
            }

            dragEvent.previousPosition != dragEvent.position -> {
                result = dragEvent
                done = true
            }
        }
    }
    return result
}

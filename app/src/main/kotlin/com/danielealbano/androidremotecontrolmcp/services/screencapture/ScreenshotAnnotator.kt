package com.danielealbano.androidremotecontrolmcp.services.screencapture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityNodeData
import javax.inject.Inject
import kotlin.math.max

/**
 * Annotates a screenshot bitmap with a real-coordinate grid and bounding boxes
 * for on-screen UI elements.
 *
 * Used by [GetScreenStateHandler] when `include_screenshot=true` (and
 * `annotate` is not disabled) so a vision-language model can read off real
 * device coordinates and reference UI elements.
 *
 * Three layers are drawn, in order:
 * 1. A grid every [GRID_STEP_PX] real pixels, split into per-cell segments,
 *    with real-coordinate number chips along the top and left edges.
 * 2. Element bounding boxes: red family for clickable elements, amber family
 *    for the rest.
 * 3. Label chips for elements that carry text or a content description
 *    (the text/description, truncated). Elements without either get a box only.
 *
 * Contrast is ADAPTIVE, never a fixed dark/light scheme: before a primitive is
 * drawn, the luminance of the actual pixels underneath its segment/region is
 * sampled from the source bitmap, and the ink is picked from the bright or dark
 * variant of its color family accordingly. Every stroke is also drawn twice —
 * a wider halo in the opposite tone (black under bright ink, white under dark
 * ink) under the core stroke — so a segment crossing a mixed background (photo,
 * gradient, mid grey) keeps a contrasting edge somewhere along its length.
 * Label chips invert against their sampled surround (light chip with dark text
 * over dark areas, dark chip with light text over bright areas).
 *
 * All drawing happens on the final, already-downscaled bitmap so text stays
 * legible at the returned resolution.
 */
@Suppress("TooManyFunctions")
class ScreenshotAnnotator
    @Inject
    constructor() {
        /**
         * Pure-Kotlin data class for scaled element bounds.
         * Avoids [RectF] (Android framework) in testable methods so unit tests
         * on JVM with `isReturnDefaultValues = true` work correctly.
         */
        internal data class ScaledBounds(
            val left: Float,
            val top: Float,
            val right: Float,
            val bottom: Float,
        )

        /** A stroke color decision for one primitive: the core ink plus whether it is a bright ink. */
        internal data class InkChoice(
            val ink: Int,
            val bright: Boolean,
        )

        /**
         * Annotates a screenshot with a coordinate grid and boxes/labels for the given elements.
         *
         * Creates a mutable copy of the input bitmap and draws on it. The input bitmap
         * is NOT modified or recycled -- the caller retains ownership of both. Background
         * luminance is sampled from the input's pixels (via a software copy when the input
         * is a HARDWARE bitmap, whose pixels cannot be read directly), so already-drawn
         * annotation strokes never feed back into later color decisions.
         *
         * @param bitmap The resized screenshot bitmap.
         * @param elements Flat list of on-screen elements to annotate (already filtered).
         * @param screenWidth Original screen width in pixels (for coordinate mapping).
         * @param screenHeight Original screen height in pixels (for coordinate mapping).
         * @return A new annotated [Bitmap]. Caller must recycle when done.
         * @throws IllegalStateException if the bitmap cannot be copied.
         * @throws IllegalArgumentException if [screenWidth] or [screenHeight] is not positive.
         */
        fun annotate(
            bitmap: Bitmap,
            elements: List<AccessibilityNodeData>,
            screenWidth: Int,
            screenHeight: Int,
        ): Bitmap {
            require(screenWidth > 0) { "screenWidth must be positive, was $screenWidth" }
            require(screenHeight > 0) { "screenHeight must be positive, was $screenHeight" }
            return annotateView(bitmap, elements, 0, 0, screenWidth, screenHeight)
        }

        /**
         * Annotates a cropped, already-enlarged view of one screen region: [bitmap] shows ONLY the
         * region (viewLeft, viewTop, viewWidth, viewHeight) of the full screen, rendered at the
         * bitmap's own resolution. Grid lines keep their REAL device coordinates (the regular
         * [GRID_STEP_PX] multiples inside the region, plus boundary lines at the region edges),
         * element boxes are intersected with the region, and elements fully outside it are skipped.
         * Because the region is enlarged, labels that would overlap in the full-screen view stay
         * distinguishable.
         *
         * Same ownership/luminance contract as [annotate].
         *
         * @throws IllegalArgumentException if the view origin is negative or its size is not positive.
         */
        @Suppress("LongParameterList")
        fun annotateRegion(
            bitmap: Bitmap,
            elements: List<AccessibilityNodeData>,
            viewLeft: Int,
            viewTop: Int,
            viewWidth: Int,
            viewHeight: Int,
        ): Bitmap {
            require(viewLeft >= 0 && viewTop >= 0) { "view origin must not be negative, was ($viewLeft, $viewTop)" }
            return annotateView(bitmap, elements, viewLeft, viewTop, viewWidth, viewHeight)
        }

        @Suppress("LongParameterList", "LongMethod", "TooGenericExceptionCaught")
        private fun annotateView(
            bitmap: Bitmap,
            elements: List<AccessibilityNodeData>,
            viewLeft: Int,
            viewTop: Int,
            viewWidth: Int,
            viewHeight: Int,
        ): Bitmap {
            require(viewWidth > 0) { "viewWidth must be positive, was $viewWidth" }
            require(viewHeight > 0) { "viewHeight must be positive, was $viewHeight" }

            // Always use ARGB_8888 for the mutable copy -- HARDWARE bitmaps cannot be mutable
            val copy =
                checkNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, true)) {
                    "Failed to create mutable bitmap copy for annotation"
                }

            // Luminance sampling below reads individual pixels, which HARDWARE bitmaps do not
            // support (getPixel throws IllegalStateException). When the source is a HARDWARE
            // bitmap -- the usual case for captured screenshots -- sample from a software copy
            // instead. The sampling source stays pristine: strokes are drawn on [copy] only.
            var sampleSource: Bitmap? = null
            try {
                val sampling =
                    if (bitmap.config == Bitmap.Config.HARDWARE) {
                        checkNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)) {
                            "Failed to create readable bitmap copy for annotation sampling"
                        }
                    } else {
                        bitmap
                    }
                sampleSource = sampling
                val canvas = Canvas(copy)
                val scaleX = copy.width.toFloat() / viewWidth.toFloat()
                val scaleY = copy.height.toFloat() / viewHeight.toFloat()
                val density = copy.width.toFloat() / REFERENCE_WIDTH_DP

                drawGrid(canvas, sampling, viewLeft, viewTop, viewWidth, viewHeight, scaleX, scaleY, density)
                drawElements(
                    canvas,
                    sampling,
                    elements,
                    copy.width,
                    copy.height,
                    viewLeft,
                    viewTop,
                    scaleX,
                    scaleY,
                    density,
                )

                return copy
            } catch (e: Exception) {
                copy.recycle()
                throw e
            } finally {
                sampleSource?.takeIf { it !== bitmap }?.recycle()
            }
        }

        // ── Grid ─────────────────────────────────────────────────────────────

        @Suppress("LongParameterList", "LongMethod")
        private fun drawGrid(
            canvas: Canvas,
            source: Bitmap,
            viewLeft: Int,
            viewTop: Int,
            viewWidth: Int,
            viewHeight: Int,
            scaleX: Float,
            scaleY: Float,
            density: Float,
        ) {
            val coreWidth = GRID_CORE_WIDTH_DP * density
            val haloWidth = coreWidth + GRID_HALO_EXTRA_DP * density
            val linePaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                }
            val textPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = GRID_TEXT_SIZE_DP * density
                    typeface = Typeface.DEFAULT_BOLD
                }

            val verticalXs = gridPositions(viewLeft, viewLeft + viewWidth)
            val horizontalYs = gridPositions(viewTop, viewTop + viewHeight)

            // Vertical lines, one adaptive decision per grid cell segment.
            for (realX in verticalXs) {
                val x = (realX - viewLeft) * scaleX
                for (i in 0 until horizontalYs.size - 1) {
                    val y0 = (horizontalYs[i] - viewTop) * scaleY
                    val y1 = (horizontalYs[i + 1] - viewTop) * scaleY
                    val lum =
                        sampleRectLuminance(
                            source,
                            (x - SAMPLE_BAND_PX).toInt(),
                            y0.toInt(),
                            (x + SAMPLE_BAND_PX).toInt(),
                            y1.toInt(),
                        )
                    val choice = inkChoice(lum, INK_GRID_BRIGHT, INK_GRID_DARK)
                    strokeSegment(canvas, linePaint, choice, haloWidth, coreWidth) {
                        canvas.drawLine(x, y0, x, y1, linePaint)
                    }
                }
            }
            // Horizontal lines, one adaptive decision per grid cell segment.
            for (realY in horizontalYs) {
                val y = (realY - viewTop) * scaleY
                for (i in 0 until verticalXs.size - 1) {
                    val x0 = (verticalXs[i] - viewLeft) * scaleX
                    val x1 = (verticalXs[i + 1] - viewLeft) * scaleX
                    val lum =
                        sampleRectLuminance(
                            source,
                            x0.toInt(),
                            (y - SAMPLE_BAND_PX).toInt(),
                            x1.toInt(),
                            (y + SAMPLE_BAND_PX).toInt(),
                        )
                    val choice = inkChoice(lum, INK_GRID_BRIGHT, INK_GRID_DARK)
                    strokeSegment(canvas, linePaint, choice, haloWidth, coreWidth) {
                        canvas.drawLine(x0, y, x1, y, linePaint)
                    }
                }
            }
            // Coordinate chips: X values along the top edge, Y values along the left edge.
            for (realX in verticalXs) {
                drawChip(
                    canvas,
                    source,
                    textPaint,
                    realX.toString(),
                    centerX = (realX - viewLeft) * scaleX,
                    centerY = null,
                    bitmapWidth = source.width,
                    bitmapHeight = source.height,
                    density = density,
                    borderColor = null,
                )
            }
            for (realY in horizontalYs.drop(1)) {
                drawChip(
                    canvas,
                    source,
                    textPaint,
                    realY.toString(),
                    centerX = null,
                    centerY = (realY - viewTop) * scaleY,
                    bitmapWidth = source.width,
                    bitmapHeight = source.height,
                    density = density,
                    borderColor = null,
                )
            }
        }

        /**
         * Grid line positions in real coordinates for the range [start, end]: the range start,
         * every [GRID_STEP_PX] multiple strictly inside the range, and the range end. For a
         * full-screen range (start = 0) this is 0, STEP, 2*STEP, …, plus the far edge.
         */
        internal fun gridPositions(
            start: Int,
            end: Int,
        ): List<Int> {
            val positions = mutableListOf(start)
            var value = (start / GRID_STEP_PX + 1) * GRID_STEP_PX
            while (value < end) {
                positions.add(value)
                value += GRID_STEP_PX
            }
            if (positions.last() != end) positions.add(end)
            return positions
        }

        // ── Elements ─────────────────────────────────────────────────────────

        @Suppress("LongParameterList", "LongMethod", "LoopWithTooManyJumpStatements")
        private fun drawElements(
            canvas: Canvas,
            source: Bitmap,
            elements: List<AccessibilityNodeData>,
            bitmapWidth: Int,
            bitmapHeight: Int,
            viewLeft: Int,
            viewTop: Int,
            scaleX: Float,
            scaleY: Float,
            density: Float,
        ) {
            if (elements.isEmpty()) return
            val coreWidth = BOX_CORE_WIDTH_DP * density
            val haloWidth = coreWidth + BOX_HALO_EXTRA_DP * density
            val boxPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                }
            val labelTextPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = LABEL_TEXT_SIZE_DP * density
                    typeface = Typeface.DEFAULT_BOLD
                }

            for (element in elements) {
                val scaled =
                    computeScaledBounds(element, scaleX, scaleY, bitmapWidth, bitmapHeight, viewLeft, viewTop)
                        ?: continue
                val rect = RectF(scaled.left, scaled.top, scaled.right, scaled.bottom)
                val lum =
                    sampleRectLuminance(
                        source,
                        (scaled.left - SAMPLE_BAND_PX).toInt(),
                        (scaled.top - SAMPLE_BAND_PX).toInt(),
                        (scaled.right + SAMPLE_BAND_PX).toInt(),
                        (scaled.bottom + SAMPLE_BAND_PX).toInt(),
                    )
                val choice =
                    if (element.clickable) {
                        inkChoice(lum, INK_CLICKABLE_BRIGHT, INK_CLICKABLE_DARK)
                    } else {
                        inkChoice(lum, INK_OTHER_BRIGHT, INK_OTHER_DARK)
                    }
                strokeSegment(canvas, boxPaint, choice, haloWidth, coreWidth) {
                    canvas.drawRect(rect, boxPaint)
                }

                val label = elementLabel(element) ?: continue
                drawElementLabel(
                    canvas,
                    source,
                    labelTextPaint,
                    label,
                    rect,
                    choice.ink,
                    bitmapWidth,
                    bitmapHeight,
                    density,
                )
            }
        }

        /** Draws the label chip for one element: above its box, or inside the box top when there is no room above. */
        @Suppress("LongParameterList")
        private fun drawElementLabel(
            canvas: Canvas,
            source: Bitmap,
            textPaint: Paint,
            label: String,
            box: RectF,
            borderColor: Int,
            bitmapWidth: Int,
            bitmapHeight: Int,
            density: Float,
        ) {
            val padX = LABEL_PADDING_X_DP * density
            val padY = LABEL_PADDING_Y_DP * density
            val textWidth = textPaint.measureText(label)
            val chipWidth = textWidth + padX * 2
            val chipHeight = textPaint.textSize + padY * 2
            var left = box.left
            var top = box.top - chipHeight
            if (top < 0f) top = box.top
            if (left + chipWidth > bitmapWidth) left = bitmapWidth - chipWidth
            if (left < 0f) left = 0f
            val chip = RectF(left, top, left + chipWidth, top + chipHeight)
            drawChipRect(canvas, source, textPaint, label, chip, density, borderColor)
        }

        // ── Chips (grid numbers + element labels) ────────────────────────────

        /**
         * Draws a small text chip pinned to the top edge (when [centerX] is given, for grid X labels)
         * or to the left edge (when [centerY] is given, for grid Y labels), clamped inside the bitmap.
         */
        @Suppress("LongParameterList")
        private fun drawChip(
            canvas: Canvas,
            source: Bitmap,
            textPaint: Paint,
            text: String,
            centerX: Float?,
            centerY: Float?,
            bitmapWidth: Int,
            bitmapHeight: Int,
            density: Float,
            borderColor: Int?,
        ) {
            val padX = LABEL_PADDING_X_DP * density
            val padY = LABEL_PADDING_Y_DP * density
            val chipWidth = textPaint.measureText(text) + padX * 2
            val chipHeight = textPaint.textSize + padY * 2
            val edge = CHIP_EDGE_OFFSET_DP * density
            var left = if (centerX != null) centerX - chipWidth / 2f else edge
            var top = if (centerY != null) centerY - chipHeight / 2f else edge
            left = left.coerceIn(0f, max(0f, bitmapWidth - chipWidth))
            top = top.coerceIn(0f, max(0f, bitmapHeight - chipHeight))
            drawChipRect(
                canvas,
                source,
                textPaint,
                text,
                RectF(left, top, left + chipWidth, top + chipHeight),
                density,
                borderColor,
            )
        }

        /**
         * Fills [chip] with the tone that inverts its sampled surround and draws [text] in the
         * opposite tone, so the chip and its glyphs contrast with whatever is underneath.
         */
        @Suppress("LongParameterList")
        private fun drawChipRect(
            canvas: Canvas,
            source: Bitmap,
            textPaint: Paint,
            text: String,
            chip: RectF,
            density: Float,
            borderColor: Int?,
        ) {
            val lum =
                sampleRectLuminance(
                    source,
                    chip.left.toInt(),
                    chip.top.toInt(),
                    chip.right.toInt(),
                    chip.bottom.toInt(),
                )
            val onDark = lum < LUMINANCE_THRESHOLD
            val fillPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = if (onDark) CHIP_FILL_ON_DARK else CHIP_FILL_ON_LIGHT
                    alpha = CHIP_FILL_ALPHA
                }
            val radius = CHIP_RADIUS_DP * density
            canvas.drawRoundRect(chip, radius, radius, fillPaint)
            if (borderColor != null) {
                val borderPaint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = CHIP_BORDER_WIDTH_DP * density
                        color = borderColor
                    }
                canvas.drawRoundRect(chip, radius, radius, borderPaint)
            }
            val previousColor = textPaint.color
            textPaint.color = if (onDark) CHIP_TEXT_ON_DARK else CHIP_TEXT_ON_LIGHT
            val padX = LABEL_PADDING_X_DP * density
            val padY = LABEL_PADDING_Y_DP * density
            canvas.drawText(text, chip.left + padX, chip.bottom - padY, textPaint)
            textPaint.color = previousColor
        }

        // ── Stroke + sampling primitives ─────────────────────────────────────

        /** Draws [draw] twice: first the halo (opposite tone, wider), then the core ink. */
        private inline fun strokeSegment(
            canvas: Canvas,
            paint: Paint,
            choice: InkChoice,
            haloWidth: Float,
            coreWidth: Float,
            draw: () -> Unit,
        ) {
            paint.color = if (choice.bright) HALO_FOR_BRIGHT_INK else HALO_FOR_DARK_INK
            paint.alpha = HALO_ALPHA
            paint.strokeWidth = haloWidth
            draw()
            paint.color = choice.ink
            paint.alpha = COLOR_ALPHA_MAX
            paint.strokeWidth = coreWidth
            draw()
        }

        /** Picks the bright variant of a color family over dark backgrounds, the dark variant over bright ones. */
        internal fun inkChoice(
            avgLuminance: Float,
            brightInk: Int,
            darkInk: Int,
        ): InkChoice =
            if (avgLuminance < LUMINANCE_THRESHOLD) {
                InkChoice(brightInk, bright = true)
            } else {
                InkChoice(darkInk, bright = false)
            }

        /**
         * Average luminance (0–255) of the source bitmap inside the given rectangle, sampled on a
         * coarse grid (at most ~[SAMPLE_GRID] steps per axis). The rectangle is clamped to the
         * bitmap; an empty intersection samples the nearest single pixel. Falls back to 0 (treated
         * as dark, i.e. bright ink) when the bitmap has no pixels.
         */
        internal fun sampleRectLuminance(
            bitmap: Bitmap,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
        ): Float {
            if (bitmap.width <= 0 || bitmap.height <= 0) return 0f
            val clampedLeft = left.coerceIn(0, bitmap.width - 1)
            val clampedTop = top.coerceIn(0, bitmap.height - 1)
            val clampedRight = right.coerceIn(clampedLeft, bitmap.width - 1)
            val clampedBottom = bottom.coerceIn(clampedTop, bitmap.height - 1)
            val stepX = max(1, (clampedRight - clampedLeft) / SAMPLE_GRID)
            val stepY = max(1, (clampedBottom - clampedTop) / SAMPLE_GRID)
            var sum = 0f
            var count = 0
            var y = clampedTop
            while (y <= clampedBottom) {
                var x = clampedLeft
                while (x <= clampedRight) {
                    sum += luminanceOfColor(bitmap.getPixel(x, y))
                    count++
                    x += stepX
                }
                y += stepY
            }
            return if (count == 0) 0f else sum / count
        }

        /** ITU-R BT.601 luma of a packed ARGB color, 0–255. */
        internal fun luminanceOfColor(color: Int): Float {
            return luminanceOfRgb(Color.red(color), Color.green(color), Color.blue(color))
        }

        /** ITU-R BT.601 luma of raw RGB channels, 0–255. Pure arithmetic, unit-testable without Android stubs. */
        internal fun luminanceOfRgb(
            red: Int,
            green: Int,
            blue: Int,
        ): Float = LUMA_RED * red + LUMA_GREEN * green + LUMA_BLUE * blue

        /**
         * The display label for an element: its text if non-blank, else its content description,
         * whitespace-collapsed and truncated to [MAX_LABEL_CHARS]. Null when the element carries
         * neither — such elements are drawn with a box only.
         */
        @Suppress("ReturnCount")
        internal fun elementLabel(element: AccessibilityNodeData): String? {
            val raw =
                element.text?.takeIf { it.isNotBlank() }
                    ?: element.contentDescription?.takeIf { it.isNotBlank() }
                    ?: return null
            val collapsed = raw.replace(WHITESPACE_REGEX, " ").trim()
            if (collapsed.isEmpty()) return null
            return if (collapsed.length > MAX_LABEL_CHARS) {
                collapsed.take(MAX_LABEL_CHARS - 1) + ELLIPSIS
            } else {
                collapsed
            }
        }

        /**
         * Computes the scaled bounding rectangle for an element on the bitmap.
         *
         * Returns a pure-Kotlin [ScaledBounds] (not [RectF]) so this method is
         * fully testable on JVM without Android framework stubs. [originX]/[originY] are the
         * real device coordinates of the bitmap's top-left corner (0, 0 for a full-screen
         * screenshot, the region origin for a cropped view): element bounds are shifted by
         * the origin before scaling, then clamped to the bitmap.
         *
         * @return Scaled [ScaledBounds] clamped to bitmap bounds, or null if fully outside.
         */
        internal fun computeScaledBounds(
            element: AccessibilityNodeData,
            scaleX: Float,
            scaleY: Float,
            bitmapWidth: Int,
            bitmapHeight: Int,
            originX: Int = 0,
            originY: Int = 0,
        ): ScaledBounds? {
            val bounds = element.bounds
            val left = ((bounds.left - originX) * scaleX).coerceIn(0f, bitmapWidth.toFloat())
            val top = ((bounds.top - originY) * scaleY).coerceIn(0f, bitmapHeight.toFloat())
            val right = ((bounds.right - originX) * scaleX).coerceIn(0f, bitmapWidth.toFloat())
            val bottom = ((bounds.bottom - originY) * scaleY).coerceIn(0f, bitmapHeight.toFloat())

            // Skip if fully collapsed after clamping
            if (right <= left || bottom <= top) return null

            return ScaledBounds(left, top, right, bottom)
        }

        /**
         * Extracts the display label from a node ID by stripping the `node_` prefix.
         */
        internal fun extractLabel(nodeId: String): String =
            if (nodeId.startsWith(NODE_ID_PREFIX)) {
                nodeId.removePrefix(NODE_ID_PREFIX)
            } else {
                nodeId
            }

        companion object {
            internal const val NODE_ID_PREFIX = "node_"
            internal const val GRID_STEP_PX = 200
            internal const val MAX_LABEL_CHARS = 18

            /** Sampled-average luminance below this value counts as a dark background. */
            internal const val LUMINANCE_THRESHOLD = 140f

            private val WHITESPACE_REGEX = Regex("\\s+")
            private const val ELLIPSIS = "…"

            // Color families: a bright variant (for dark backgrounds) and a dark variant
            // (for bright backgrounds) per family. Halo tone follows the chosen variant.
            private val INK_GRID_BRIGHT = Color.rgb(238, 238, 238)
            private val INK_GRID_DARK = Color.rgb(32, 32, 32)
            private val INK_CLICKABLE_BRIGHT = Color.rgb(255, 64, 64)
            private val INK_CLICKABLE_DARK = Color.rgb(171, 0, 28)
            private val INK_OTHER_BRIGHT = Color.rgb(255, 200, 40)
            private val INK_OTHER_DARK = Color.rgb(150, 98, 0)
            private val HALO_FOR_BRIGHT_INK = Color.BLACK
            private val HALO_FOR_DARK_INK = Color.WHITE
            private val CHIP_FILL_ON_DARK = Color.rgb(250, 250, 250)
            private val CHIP_FILL_ON_LIGHT = Color.rgb(16, 16, 16)
            private val CHIP_TEXT_ON_DARK = Color.rgb(16, 16, 16)
            private val CHIP_TEXT_ON_LIGHT = Color.rgb(250, 250, 250)

            private const val HALO_ALPHA = 170
            private const val CHIP_FILL_ALPHA = 235
            private const val COLOR_ALPHA_MAX = 255

            private const val LUMA_RED = 0.299f
            private const val LUMA_GREEN = 0.587f
            private const val LUMA_BLUE = 0.114f

            private const val SAMPLE_GRID = 10
            private const val SAMPLE_BAND_PX = 2f

            private const val GRID_CORE_WIDTH_DP = 0.8f
            private const val GRID_HALO_EXTRA_DP = 1.6f
            private const val GRID_TEXT_SIZE_DP = 9f
            private const val BOX_CORE_WIDTH_DP = 1.6f
            private const val BOX_HALO_EXTRA_DP = 1.6f
            private const val LABEL_TEXT_SIZE_DP = 10f
            private const val LABEL_PADDING_X_DP = 3f
            private const val LABEL_PADDING_Y_DP = 1.5f
            private const val CHIP_EDGE_OFFSET_DP = 2f
            private const val CHIP_RADIUS_DP = 2f
            private const val CHIP_BORDER_WIDTH_DP = 1f
            private const val REFERENCE_WIDTH_DP = 360f
        }
    }

package com.danielealbano.androidremotecontrolmcp.services.screencapture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityNodeData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.BoundsData
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ScreenshotAnnotatorTest")
class ScreenshotAnnotatorTest {
    private val annotator = ScreenshotAnnotator()

    @Suppress("LongParameterList")
    private fun makeNode(
        id: String = "node_test",
        className: String = "android.widget.Button",
        text: String? = null,
        contentDescription: String? = null,
        resourceId: String? = null,
        bounds: BoundsData = BoundsData(0, 0, 100, 100),
        visible: Boolean = true,
        clickable: Boolean = false,
        longClickable: Boolean = false,
        focusable: Boolean = false,
        scrollable: Boolean = false,
        editable: Boolean = false,
        enabled: Boolean = false,
        children: List<AccessibilityNodeData> = emptyList(),
    ): AccessibilityNodeData =
        AccessibilityNodeData(
            id = id,
            className = className,
            text = text,
            contentDescription = contentDescription,
            resourceId = resourceId,
            bounds = bounds,
            visible = visible,
            clickable = clickable,
            longClickable = longClickable,
            focusable = focusable,
            scrollable = scrollable,
            editable = editable,
            enabled = enabled,
            children = children,
        )

    @Nested
    @DisplayName("extractLabel")
    inner class ExtractLabelTests {
        @Test
        fun `strips node_ prefix`() {
            assertEquals("a3f2", annotator.extractLabel("node_a3f2"))
        }

        @Test
        fun `returns full id when no prefix`() {
            assertEquals("custom_id", annotator.extractLabel("custom_id"))
        }

        @Test
        fun `handles empty string`() {
            assertEquals("", annotator.extractLabel(""))
        }

        @Test
        fun `handles node_ prefix only (empty hash)`() {
            assertEquals("", annotator.extractLabel("node_"))
        }
    }

    @Nested
    @DisplayName("computeScaledBounds")
    inner class ComputeScaledBoundsTests {
        @Test
        fun `correctly scales coordinates`() {
            // Screen 1080x2400, bitmap 540x1200 → scaleX=0.5, scaleY=0.5
            val element = makeNode(bounds = BoundsData(100, 200, 300, 400))
            val result = annotator.computeScaledBounds(element, 0.5f, 0.5f, 540, 1200)
            assertNotNull(result)
            assertEquals(50f, result!!.left)
            assertEquals(100f, result.top)
            assertEquals(150f, result.right)
            assertEquals(200f, result.bottom)
        }

        @Test
        fun `returns null for element fully outside bitmap`() {
            val element = makeNode(bounds = BoundsData(2000, 3000, 2100, 3100))
            val result = annotator.computeScaledBounds(element, 0.5f, 0.5f, 540, 1200)
            assertNull(result)
        }

        @Test
        fun `clamps partially outside element`() {
            val element = makeNode(bounds = BoundsData(1000, 2300, 1200, 2600))
            val result = annotator.computeScaledBounds(element, 0.5f, 0.5f, 540, 1200)
            assertNotNull(result)
            assertEquals(500f, result!!.left)
            assertEquals(540f, result.right) // clamped to bitmapWidth
        }

        @Test
        fun `returns null when zero-width element maps to same edge`() {
            // Element with left == right at screen edge
            val element = makeNode(bounds = BoundsData(1080, 0, 1080, 100))
            val result = annotator.computeScaledBounds(element, 0.5f, 0.5f, 540, 1200)
            assertNull(result)
        }

        @Test
        fun `returns null when non-zero-width element collapses after clamping`() {
            // Element has width (1080 to 1200) but after scale (0.5) both map to 540+
            // and clamp to bitmapWidth=540, resulting in left==right==540
            val element = makeNode(bounds = BoundsData(1080, 0, 1200, 100))
            val result = annotator.computeScaledBounds(element, 0.5f, 0.5f, 540, 1200)
            assertNull(result)
        }

        @Test
        fun `shifts bounds by the view origin for a cropped region`() {
            // Region view starting at (200, 400); the bitmap shows the region at scale 1
            val element = makeNode(bounds = BoundsData(300, 500, 500, 700))
            val result = annotator.computeScaledBounds(element, 1f, 1f, 400, 400, originX = 200, originY = 400)
            assertNotNull(result)
            assertEquals(100f, result!!.left)
            assertEquals(100f, result.top)
            assertEquals(300f, result.right)
            assertEquals(300f, result.bottom)
        }

        @Test
        fun `returns null for element fully outside the view`() {
            val element = makeNode(bounds = BoundsData(0, 0, 100, 100))
            val result = annotator.computeScaledBounds(element, 1f, 1f, 400, 400, originX = 200, originY = 400)
            assertNull(result)
        }

        @Test
        fun `clamps element straddling the view edge`() {
            val element = makeNode(bounds = BoundsData(100, 300, 300, 500))
            val result = annotator.computeScaledBounds(element, 1f, 1f, 400, 400, originX = 200, originY = 400)
            assertNotNull(result)
            assertEquals(0f, result!!.left)
            assertEquals(0f, result.top)
            assertEquals(100f, result.right)
            assertEquals(100f, result.bottom)
        }
    }

    @Nested
    @DisplayName("gridPositions")
    inner class GridPositionsTests {
        @Test
        fun `full screen range starts at zero and appends the far edge`() {
            assertEquals(
                listOf(0, 200, 400, 600, 800, 1000, 1200, 1400, 1600, 1768),
                annotator.gridPositions(0, 1768),
            )
        }

        @Test
        fun `range edges are kept and interior multiples use real coordinates`() {
            assertEquals(listOf(100, 200, 400, 600), annotator.gridPositions(100, 600))
            assertEquals(listOf(200, 400, 600, 800), annotator.gridPositions(200, 800))
        }

        @Test
        fun `range smaller than one step has only its two edges`() {
            assertEquals(listOf(50, 150), annotator.gridPositions(50, 150))
        }
    }

    @Nested
    @DisplayName("annotate")
    inner class AnnotateTests {
        private lateinit var mockBitmap: Bitmap
        private lateinit var mockCopy: Bitmap

        @BeforeEach
        fun setup() {
            mockBitmap = mockk(relaxed = true)
            mockCopy = mockk(relaxed = true)
            every { mockBitmap.width } returns 540
            every { mockBitmap.height } returns 1200
            // annotate() draws on an ARGB_8888 copy; the input config only decides whether
            // luminance sampling needs its own readable copy (HARDWARE inputs)
            every { mockBitmap.config } returns Bitmap.Config.ARGB_8888
            every { mockBitmap.copy(Bitmap.Config.ARGB_8888, true) } returns mockCopy
            every { mockCopy.width } returns 540
            every { mockCopy.height } returns 1200
            mockkConstructor(Canvas::class)
            every { anyConstructed<Canvas>().drawRoundRect(any(), any(), any(), any()) } just Runs
            every { anyConstructed<Canvas>().drawRect(any<RectF>(), any()) } just Runs
            every { anyConstructed<Canvas>().drawLine(any(), any(), any(), any(), any()) } just Runs
            every { anyConstructed<Canvas>().drawText(any(), any(), any(), any()) } just Runs
        }

        @AfterEach
        fun teardown() {
            unmockkConstructor(Canvas::class)
        }

        @Test
        fun `empty elements returns copy with grid drawn`() {
            val result = annotator.annotate(mockBitmap, emptyList(), 1080, 2400)
            assertEquals(mockCopy, result)
            verify(exactly = 0) { mockCopy.recycle() } // input not recycled
            // The coordinate grid is part of the annotation even with no elements
            verify(atLeast = 1) { anyConstructed<Canvas>().drawLine(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `single element draws box and label`() {
            val element =
                makeNode(id = "node_a3f2", text = "Settings", bounds = BoundsData(100, 200, 300, 400), visible = true)
            annotator.annotate(mockBitmap, listOf(element), 1080, 2400)
            // Halo + core strokes => at least two drawRect calls for the one box
            verify(atLeast = 2) { anyConstructed<Canvas>().drawRect(any<RectF>(), any()) }
            verify(atLeast = 1) {
                anyConstructed<Canvas>().drawText(match { it.contains("Settings") }, any(), any(), any())
            }
        }

        @Test
        fun `element without text or description draws box but no label`() {
            val element = makeNode(id = "node_icon", bounds = BoundsData(100, 200, 300, 400), visible = true)
            annotator.annotate(mockBitmap, listOf(element), 1080, 2400)
            verify(atLeast = 2) { anyConstructed<Canvas>().drawRect(any<RectF>(), any()) }
            // Only grid axis numbers are drawn as text; no element label
            verify(exactly = 0) {
                anyConstructed<Canvas>().drawText(match { it.contains("icon") }, any(), any(), any())
            }
        }

        @Test
        fun `multiple elements draws box and label for each`() {
            val element1 =
                makeNode(id = "node_a3f2", text = "First", bounds = BoundsData(100, 200, 300, 400), visible = true)
            val element2 =
                makeNode(
                    id = "node_b4c5",
                    contentDescription = "Second",
                    bounds = BoundsData(500, 600, 700, 800),
                    visible = true,
                )
            annotator.annotate(mockBitmap, listOf(element1, element2), 1080, 2400)
            verify(atLeast = 4) { anyConstructed<Canvas>().drawRect(any<RectF>(), any()) }
            verify(atLeast = 1) {
                anyConstructed<Canvas>().drawText(match { it.contains("First") }, any(), any(), any())
            }
            verify(atLeast = 1) {
                anyConstructed<Canvas>().drawText(match { it.contains("Second") }, any(), any(), any())
            }
        }

        @Test
        fun `region view labels in-region elements and skips out-of-region ones`() {
            // The bitmap (540x1200 in setup) shows region (1000, 2000)-(2080, 4400) of the screen
            val inside =
                makeNode(id = "node_in", text = "Inside", bounds = BoundsData(1100, 2200, 1300, 2400), visible = true)
            val outside =
                makeNode(id = "node_out", text = "Outside", bounds = BoundsData(0, 0, 100, 100), visible = true)
            val result = annotator.annotateRegion(mockBitmap, listOf(inside, outside), 1000, 2000, 1080, 2400)
            assertEquals(mockCopy, result)
            verify(atLeast = 1) {
                anyConstructed<Canvas>().drawText(match { it.contains("Inside") }, any(), any(), any())
            }
            verify(exactly = 0) {
                anyConstructed<Canvas>().drawText(match { it.contains("Outside") }, any(), any(), any())
            }
            // The grid keeps real coordinates: the region's left edge appears as an X chip
            verify(atLeast = 1) {
                anyConstructed<Canvas>().drawText("1000", any(), any(), any())
            }
        }

        @Test
        fun `region view rejects a negative origin`() {
            var thrown = false
            try {
                annotator.annotateRegion(mockBitmap, emptyList(), -1, 0, 100, 100)
            } catch (_: IllegalArgumentException) {
                thrown = true
            }
            assertTrue(thrown, "Should have thrown IllegalArgumentException")
        }

        @Test
        fun `region view rejects a non-positive size`() {
            var thrown = false
            try {
                annotator.annotateRegion(mockBitmap, emptyList(), 0, 0, 0, 100)
            } catch (_: IllegalArgumentException) {
                thrown = true
            }
            assertTrue(thrown, "Should have thrown IllegalArgumentException")
        }

        @Test
        fun `hardware bitmap is sampled through a software copy`() {
            // Captured screenshots are HARDWARE bitmaps, whose pixels cannot be read
            // (getPixel throws). Sampling must go through a readable copy of the input.
            val softwareCopy = mockk<Bitmap>(relaxed = true)
            every { softwareCopy.width } returns 540
            every { softwareCopy.height } returns 1200
            every { mockBitmap.config } returns Bitmap.Config.HARDWARE
            every { mockBitmap.copy(Bitmap.Config.ARGB_8888, false) } returns softwareCopy

            val result = annotator.annotate(mockBitmap, emptyList(), 1080, 2400)

            assertEquals(mockCopy, result)
            verify(exactly = 1) { mockBitmap.copy(Bitmap.Config.ARGB_8888, false) }
            verify(atLeast = 1) { softwareCopy.getPixel(any(), any()) }
            verify(exactly = 0) { mockBitmap.getPixel(any(), any()) }
            // The sampling copy is recycled; the input bitmap is not
            verify(exactly = 1) { softwareCopy.recycle() }
        }

        @Test
        fun `does not mutate input bitmap`() {
            val element = makeNode(id = "node_x1y2", bounds = BoundsData(100, 200, 300, 400), visible = true)
            val result = annotator.annotate(mockBitmap, listOf(element), 1080, 2400)
            assertNotSame(mockBitmap, result)
            assertEquals(mockCopy, result)
        }

        @Test
        fun `recycles copy bitmap when drawing fails`() {
            // Simulate Canvas drawing throwing
            every { anyConstructed<Canvas>().drawRect(any<RectF>(), any()) } throws
                RuntimeException("Draw failed")
            val element = makeNode(id = "node_fail", bounds = BoundsData(100, 200, 300, 400), visible = true)

            var thrown = false
            try {
                annotator.annotate(mockBitmap, listOf(element), 1080, 2400)
            } catch (_: RuntimeException) {
                thrown = true
            }
            assertTrue(thrown, "Should have thrown RuntimeException")
            // The copy bitmap should be recycled despite the exception
            verify(exactly = 1) { mockCopy.recycle() }
        }

        @Test
        fun `throws IllegalStateException when bitmap copy returns null`() {
            every { mockBitmap.copy(Bitmap.Config.ARGB_8888, true) } returns null
            val element = makeNode(id = "node_test", bounds = BoundsData(100, 200, 300, 400), visible = true)

            val exception =
                org.junit.jupiter.api.assertThrows<IllegalStateException> {
                    annotator.annotate(mockBitmap, listOf(element), 1080, 2400)
                }
            assertTrue(exception.message!!.contains("Failed to create mutable bitmap copy"))
        }

        @Test
        fun `throws IllegalStateException when bitmap copy returns null for empty elements`() {
            every { mockBitmap.copy(Bitmap.Config.ARGB_8888, true) } returns null

            val exception =
                org.junit.jupiter.api.assertThrows<IllegalStateException> {
                    annotator.annotate(mockBitmap, emptyList(), 1080, 2400)
                }
            assertTrue(exception.message!!.contains("Failed to create mutable bitmap copy"))
        }

        @Test
        fun `throws IllegalArgumentException when screenWidth is zero`() {
            val exception =
                org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                    annotator.annotate(mockBitmap, emptyList(), 0, 2400)
                }
            assertTrue(exception.message!!.contains("screenWidth must be positive"))
        }

        @Test
        fun `throws IllegalArgumentException when screenHeight is zero`() {
            val exception =
                org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                    annotator.annotate(mockBitmap, emptyList(), 1080, 0)
                }
            assertTrue(exception.message!!.contains("screenHeight must be positive"))
        }

        @Test
        fun `throws IllegalArgumentException when screenWidth is negative`() {
            val exception =
                org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                    annotator.annotate(mockBitmap, emptyList(), -1, 2400)
                }
            assertTrue(exception.message!!.contains("screenWidth must be positive"))
        }

        @Test
        fun `throws IllegalArgumentException when screenHeight is negative`() {
            val exception =
                org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                    annotator.annotate(mockBitmap, emptyList(), 1080, -1)
                }
            assertTrue(exception.message!!.contains("screenHeight must be positive"))
        }
    }

    @Nested
    @DisplayName("elementLabel")
    inner class ElementLabelTests {
        @Test
        fun `prefers text over content description`() {
            val node = makeNode(text = "Hello", contentDescription = "Desc")
            assertEquals("Hello", annotator.elementLabel(node))
        }

        @Test
        fun `falls back to content description`() {
            val node = makeNode(text = "  ", contentDescription = "Desc")
            assertEquals("Desc", annotator.elementLabel(node))
        }

        @Test
        fun `null when neither text nor description`() {
            assertNull(annotator.elementLabel(makeNode()))
            assertNull(annotator.elementLabel(makeNode(text = "", contentDescription = " ")))
        }

        @Test
        fun `collapses whitespace and newlines`() {
            val node = makeNode(text = "Line one\n  Line\ttwo")
            assertEquals("Line one Line two", annotator.elementLabel(node))
        }

        @Test
        fun `truncates long labels with ellipsis`() {
            val node = makeNode(text = "A very long label indeed")
            val label = annotator.elementLabel(node)
            assertNotNull(label)
            assertEquals(ScreenshotAnnotator.MAX_LABEL_CHARS, label!!.length)
            assertTrue(label.endsWith("…"))
        }
    }

    @Nested
    @DisplayName("luminance and ink choice")
    inner class LuminanceTests {
        @Test
        fun `black has zero luminance and white is full`() {
            assertEquals(0f, annotator.luminanceOfRgb(0, 0, 0), 0.001f)
            assertEquals(255f, annotator.luminanceOfRgb(255, 255, 255), 0.01f)
        }

        @Test
        fun `grey luminance equals its channel value`() {
            assertEquals(100f, annotator.luminanceOfRgb(100, 100, 100), 0.01f)
        }

        @Test
        fun `dark background picks the bright ink`() {
            val choice = annotator.inkChoice(0f, brightInk = 111, darkInk = 222)
            assertEquals(111, choice.ink)
            assertTrue(choice.bright)
        }

        @Test
        fun `bright background picks the dark ink`() {
            val choice = annotator.inkChoice(255f, brightInk = 111, darkInk = 222)
            assertEquals(222, choice.ink)
            assertTrue(!choice.bright)
        }

        @Test
        fun `threshold boundary counts as bright background`() {
            val choice =
                annotator.inkChoice(ScreenshotAnnotator.LUMINANCE_THRESHOLD, brightInk = 111, darkInk = 222)
            assertEquals(222, choice.ink)
        }
    }
}

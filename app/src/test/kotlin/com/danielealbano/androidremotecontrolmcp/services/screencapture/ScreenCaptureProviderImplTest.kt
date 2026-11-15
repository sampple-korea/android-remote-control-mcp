package com.danielealbano.androidremotecontrolmcp.services.screencapture

import android.graphics.Bitmap
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityServiceProvider
import com.danielealbano.androidremotecontrolmcp.services.accessibility.McpAccessibilityService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ScreenCaptureProviderImplTest")
class ScreenCaptureProviderImplTest {
    private lateinit var screenshotEncoder: ScreenshotEncoder
    private lateinit var mockApiLevelProvider: ApiLevelProvider
    private lateinit var mockAccessibilityServiceProvider: AccessibilityServiceProvider
    private lateinit var mockRootScreenCapture: RootScreenCapture
    private lateinit var provider: ScreenCaptureProviderImpl
    private lateinit var mockService: McpAccessibilityService

    @BeforeEach
    fun setup() {
        screenshotEncoder = mockk(relaxed = true)
        mockApiLevelProvider = mockk()
        mockAccessibilityServiceProvider = mockk()
        // Root capture is unavailable by default: existing tests exercise the accessibility path
        mockRootScreenCapture = mockk()
        coEvery { mockRootScreenCapture.captureBitmap() } returns null
        every { mockRootScreenCapture.hasSuBinary() } returns false
        // Return API 30 (Android R) — minimum for screenshot capability
        every { mockApiLevelProvider.getSdkInt() } returns 30

        mockService = mockk(relaxed = true)
        every { mockService.canTakeScreenshot() } returns true

        // Configure the AccessibilityServiceProvider to report ready and return the mock service
        every { mockAccessibilityServiceProvider.isReady() } returns true
        every { mockAccessibilityServiceProvider.getContext() } returns mockService

        provider =
            ScreenCaptureProviderImpl(
                screenshotEncoder,
                mockApiLevelProvider,
                mockAccessibilityServiceProvider,
                mockRootScreenCapture,
            )
    }

    @Nested
    @DisplayName("captureScreenshotBitmap")
    inner class CaptureScreenshotBitmapTests {
        @Test
        fun `returns resized bitmap on success`() =
            runTest {
                val originalBitmap = mockk<Bitmap>(relaxed = true)
                val resizedBitmap = mockk<Bitmap>(relaxed = true)
                coEvery { mockService.takeScreenshotBitmap() } returns originalBitmap
                every {
                    screenshotEncoder.resizeBitmapProportional(originalBitmap, 700, 700)
                } returns resizedBitmap

                val result = provider.captureScreenshotBitmap(maxWidth = 700, maxHeight = 700)

                assertTrue(result.isSuccess)
                assertEquals(resizedBitmap, result.getOrNull())
                // Original bitmap recycled because a new (different) one was produced
                verify(exactly = 1) { originalBitmap.recycle() }
            }

        @Test
        fun `does not recycle bitmap when resize returns same instance`() =
            runTest {
                val bitmap = mockk<Bitmap>(relaxed = true)
                coEvery { mockService.takeScreenshotBitmap() } returns bitmap
                every {
                    screenshotEncoder.resizeBitmapProportional(bitmap, 700, 700)
                } returns bitmap // same instance

                val result = provider.captureScreenshotBitmap(maxWidth = 700, maxHeight = 700)

                assertTrue(result.isSuccess)
                verify(exactly = 0) { bitmap.recycle() }
            }

        @Test
        fun `returns failure when takeScreenshotBitmap returns null`() =
            runTest {
                coEvery { mockService.takeScreenshotBitmap() } returns null

                val result = provider.captureScreenshotBitmap(maxWidth = 700, maxHeight = 700)

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull()?.message?.contains("Screenshot capture failed") == true)
            }

        @Test
        fun `returns failure when service not available`() =
            runTest {
                every { mockAccessibilityServiceProvider.isReady() } returns false

                val result = provider.captureScreenshotBitmap(maxWidth = 700, maxHeight = 700)

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull()?.message?.contains("Accessibility service not enabled") == true)
            }

        @Test
        fun `recycles original bitmap when resize throws`() =
            runTest {
                val bitmap = mockk<Bitmap>(relaxed = true)
                coEvery { mockService.takeScreenshotBitmap() } returns bitmap
                every {
                    screenshotEncoder.resizeBitmapProportional(bitmap, any(), any())
                } throws RuntimeException("Resize failed")

                val result = provider.captureScreenshotBitmap(maxWidth = 700, maxHeight = 700)

                assertTrue(result.isFailure)
                // Error message is generic (does not leak internal exception details)
                assertTrue(result.exceptionOrNull()?.message == "Screenshot resize failed")
                // Original bitmap must be recycled even on resize failure
                verify(exactly = 1) { bitmap.recycle() }
            }

        @Test
        fun `returns failure when API level below 30`() =
            runTest {
                every { mockApiLevelProvider.getSdkInt() } returns 29

                val result = provider.captureScreenshotBitmap(maxWidth = 700, maxHeight = 700)

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull()?.message?.contains("Android 11") == true)
            }

        @Test
        fun `uses root capture without consulting the accessibility service`() =
            runTest {
                val bitmap = mockk<Bitmap>(relaxed = true)
                coEvery { mockRootScreenCapture.captureBitmap() } returns bitmap
                every { screenshotEncoder.resizeBitmapProportional(bitmap, 700, 700) } returns bitmap
                // Accessibility is down: the root path must still deliver
                every { mockAccessibilityServiceProvider.isReady() } returns false

                val result = provider.captureScreenshotBitmap(maxWidth = 700, maxHeight = 700)

                assertTrue(result.isSuccess)
                assertEquals(bitmap, result.getOrNull())
                coVerify(exactly = 0) { mockService.takeScreenshotBitmap() }
            }

        @Test
        fun `falls back to accessibility when root capture returns null`() =
            runTest {
                val bitmap = mockk<Bitmap>(relaxed = true)
                coEvery { mockService.takeScreenshotBitmap() } returns bitmap
                every { screenshotEncoder.resizeBitmapProportional(bitmap, null, null) } returns bitmap

                val result = provider.captureScreenshotBitmap(maxWidth = null, maxHeight = null)

                assertTrue(result.isSuccess)
                coVerify(exactly = 1) { mockService.takeScreenshotBitmap() }
            }

        @Test
        fun `passes through null maxWidth and maxHeight`() =
            runTest {
                val bitmap = mockk<Bitmap>(relaxed = true)
                coEvery { mockService.takeScreenshotBitmap() } returns bitmap
                every {
                    screenshotEncoder.resizeBitmapProportional(bitmap, null, null)
                } returns bitmap // no resize needed

                val result = provider.captureScreenshotBitmap(maxWidth = null, maxHeight = null)

                assertTrue(result.isSuccess)
                verify(exactly = 0) { bitmap.recycle() }
            }
    }

    @Nested
    @DisplayName("isScreenCaptureAvailable")
    inner class IsScreenCaptureAvailableTests {
        @Test
        fun `true when a su binary exists even if accessibility is not ready`() {
            every { mockRootScreenCapture.hasSuBinary() } returns true
            every { mockAccessibilityServiceProvider.isReady() } returns false

            assertTrue(provider.isScreenCaptureAvailable())
        }

        @Test
        fun `false when no su binary and accessibility is not ready`() {
            every { mockAccessibilityServiceProvider.isReady() } returns false

            assertFalse(provider.isScreenCaptureAvailable())
        }
    }
}

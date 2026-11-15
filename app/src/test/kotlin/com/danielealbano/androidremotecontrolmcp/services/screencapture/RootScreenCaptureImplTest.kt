package com.danielealbano.androidremotecontrolmcp.services.screencapture

import android.graphics.Bitmap
import com.danielealbano.androidremotecontrolmcp.services.shell.SuProbeResult
import com.danielealbano.androidremotecontrolmcp.services.shell.SuShell
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RootScreenCaptureImplTest")
class RootScreenCaptureImplTest {
    private lateinit var mockSu: SuShell
    private var runnerBytes: ByteArray? = null
    private var decodedBitmap: Bitmap? = null
    private var nowMs = 0L
    private lateinit var capture: RootScreenCaptureImpl

    private val pngBytes =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02, 0x03)

    @BeforeEach
    fun setup() {
        mockSu = mockk()
        runnerBytes = pngBytes
        decodedBitmap = mockk(relaxed = true)
        nowMs = 0L
        capture =
            RootScreenCaptureImpl(
                su = mockSu,
                runner = ScreencapRunner { runnerBytes },
                decoder = PngBitmapDecoder { decodedBitmap },
                clock = { nowMs },
            )
    }

    private fun stubSuWorks() {
        every { mockSu.locate() } returns "/product/bin/su"
        coEvery { mockSu.probe() } returns SuProbeResult.Works
    }

    @Test
    fun `hasSuBinary reflects locate`() {
        every { mockSu.locate() } returns "/product/bin/su"
        assertTrue(capture.hasSuBinary())

        every { mockSu.locate() } returns null
        assertFalse(capture.hasSuBinary())
    }

    @Test
    fun `unavailable and no probe when su binary is missing`() =
        runTest {
            every { mockSu.locate() } returns null

            assertFalse(capture.isAvailable())
            assertNull(capture.captureBitmap())
            coVerify(exactly = 0) { mockSu.probe() }
        }

    @Test
    fun `unavailable when the probe is denied`() =
        runTest {
            every { mockSu.locate() } returns "/product/bin/su"
            coEvery { mockSu.probe() } returns SuProbeResult.Fails("denied")

            assertFalse(capture.isAvailable())
            assertNull(capture.captureBitmap())
        }

    @Test
    fun `captures the decoded bitmap when root works`() =
        runTest {
            stubSuWorks()

            assertTrue(capture.isAvailable())
            assertEquals(decodedBitmap, capture.captureBitmap())
        }

    @Test
    fun `returns null when screencap output is not a PNG`() =
        runTest {
            stubSuWorks()
            runnerBytes = "not a png".toByteArray()

            assertNull(capture.captureBitmap())
        }

    @Test
    fun `returns null when screencap produces nothing`() =
        runTest {
            stubSuWorks()
            runnerBytes = null

            assertNull(capture.captureBitmap())
        }

    @Test
    fun `returns null when PNG decoding fails`() =
        runTest {
            stubSuWorks()
            decodedBitmap = null

            assertNull(capture.captureBitmap())
        }

    @Test
    fun `positive probe result is cached for a minute`() =
        runTest {
            stubSuWorks()

            assertTrue(capture.isAvailable())
            nowMs = 59_999L
            assertTrue(capture.isAvailable())
            coVerify(exactly = 1) { mockSu.probe() }

            nowMs = 60_001L
            assertTrue(capture.isAvailable())
            coVerify(exactly = 2) { mockSu.probe() }
        }

    @Test
    fun `negative probe result is retried after a short cache`() =
        runTest {
            every { mockSu.locate() } returns "/product/bin/su"
            coEvery { mockSu.probe() } returns SuProbeResult.Fails("denied")

            assertFalse(capture.isAvailable())
            nowMs = 4_999L
            assertFalse(capture.isAvailable())
            coVerify(exactly = 1) { mockSu.probe() }

            nowMs = 5_001L
            assertFalse(capture.isAvailable())
            coVerify(exactly = 2) { mockSu.probe() }
        }

    @Test
    fun `isPng validates the signature and a body`() {
        assertTrue(isPng(pngBytes))
        assertFalse(isPng(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertFalse(isPng("not a png".toByteArray()))
        assertFalse(isPng(ByteArray(0)))
    }
}

package com.danielealbano.androidremotecontrolmcp.services.screencapture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import com.danielealbano.androidremotecontrolmcp.services.shell.SuProbeResult
import com.danielealbano.androidremotecontrolmcp.services.shell.SuShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privileged screen capture used when the device offers a working `su` (root).
 *
 * The accessibility capture path ([McpAccessibilityService.takeScreenshotBitmap]) is limited by
 * the platform: requests must be spaced at least 334 ms apart, go through a callback that can
 * time out, and only work while the accessibility service is connected with screenshot support.
 * With root none of that applies — the system `screencap` binary asks SurfaceFlinger for the
 * default display directly (the same compositor source scrcpy's SurfaceControl capture taps)
 * and writes a lossless PNG, so captures are faster, unpaced and independent of the
 * accessibility service.
 *
 * The PNG is piped over the process stdout: it never touches a file, so there is no scratch
 * path to clean up and no on-disk copy of the screen. Any failure (no `su`, probe denied,
 * non-zero exit, timeout, non-PNG output) returns null and the caller falls back to the
 * accessibility path, which keeps non-rooted devices working exactly as before.
 */
interface RootScreenCapture {
    /** Cheap check: a `su` binary exists. Does not prove root was granted. */
    fun hasSuBinary(): Boolean

    /** True when `su` exists and a probe (`id -u` == 0) succeeded. The result is cached. */
    suspend fun isAvailable(): Boolean

    /** Captures the default display as a [Bitmap], or null when the root path cannot deliver. */
    suspend fun captureBitmap(): Bitmap?
}

/** Runs `screencap -p` under a given `su` binary and returns its stdout, or null on failure. */
fun interface ScreencapRunner {
    suspend fun run(suPath: String): ByteArray?
}

/** Decodes PNG bytes into a [Bitmap]; a seam so JVM tests do not need a real [BitmapFactory]. */
fun interface PngBitmapDecoder {
    fun decode(pngBytes: ByteArray): Bitmap?
}

class BitmapFactoryPngDecoder
    @Inject
    constructor() : PngBitmapDecoder {
        override fun decode(pngBytes: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
    }

/**
 * Production [ScreencapRunner]: spawns `su -c "exec screencap -p 2>/dev/null"` and reads the
 * PNG from its stdout. screencap's stderr is redirected to /dev/null inside the shell —
 * merging it into stdout would corrupt the PNG, and Android's ProcessBuilder has no
 * Redirect.DISCARD. Reading happens on the IO dispatcher while a timeout bounds the whole
 * run; on timeout the process is destroyed, which also closes the pipe and unblocks the reader.
 */
class ProcessScreencapRunner
    @Inject
    constructor() : ScreencapRunner {
        @Suppress("ReturnCount")
        override suspend fun run(suPath: String): ByteArray? {
            val process =
                try {
                    ProcessBuilder(suPath, "-c", SCREENCAP_COMMAND).start()
                } catch (e: IOException) {
                    Log.w(TAG, "Could not start su for screencap", e)
                    return null
                }
            process.outputStream.close()
            val bytes =
                withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) { readCapped(process) }
                }
            if (bytes == null) {
                process.destroyForcibly()
                Log.w(TAG, "Root screencap timed out or exceeded the size cap")
                return null
            }
            val exitCode =
                withTimeoutOrNull(EXIT_WAIT_TIMEOUT_MS) { withContext(Dispatchers.IO) { process.waitFor() } }
            if (exitCode != 0) {
                process.destroyForcibly()
                Log.w(TAG, "Root screencap exited with $exitCode")
                return null
            }
            return bytes
        }

        /** Reads stdout fully, giving up (null) once more than [MAX_PNG_BYTES] arrive. */
        private fun readCapped(process: Process): ByteArray? {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(READ_BUFFER_BYTES)
            while (true) {
                val read = process.inputStream.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                if (output.size() > MAX_PNG_BYTES) return null
            }
            return output.toByteArray()
        }

        companion object {
            private const val TAG = "MCP:RootScreencap"
            private const val SCREENCAP_COMMAND = "exec screencap -p 2>/dev/null"
            private const val CAPTURE_TIMEOUT_MS = 5_000L
            private const val EXIT_WAIT_TIMEOUT_MS = 1_000L
            private const val READ_BUFFER_BYTES = 64 * 1024

            /** A native-resolution PNG of the largest supported displays stays well below this. */
            private const val MAX_PNG_BYTES = 32 * 1024 * 1024
        }
    }

@Singleton
class RootScreenCaptureImpl
    constructor(
        private val su: SuShell,
        private val runner: ScreencapRunner,
        private val decoder: PngBitmapDecoder,
        private val clock: () -> Long,
    ) : RootScreenCapture {
        @Inject
        constructor(
            su: SuShell,
            runner: ScreencapRunner,
            decoder: PngBitmapDecoder,
        ) : this(su, runner, decoder, SystemClock::elapsedRealtime)

        @Volatile
        private var cachedProbe: Pair<Boolean, Long>? = null

        override fun hasSuBinary(): Boolean = su.locate() != null

        @Suppress("ReturnCount")
        override suspend fun isAvailable(): Boolean {
            val suPath = su.locate() ?: return false
            val cached = cachedProbe
            if (cached != null && clock() - cached.second < cacheTtlMs(cached.first)) {
                return cached.first
            }
            val works = su.probe() is SuProbeResult.Works
            cachedProbe = works to clock()
            return works
        }

        @Suppress("ReturnCount")
        override suspend fun captureBitmap(): Bitmap? {
            if (!isAvailable()) return null
            val suPath = su.locate() ?: return null
            val pngBytes = runner.run(suPath) ?: return null
            if (!isPng(pngBytes)) {
                Log.w(TAG, "Root screencap did not return a PNG (${pngBytes.size} bytes)")
                return null
            }
            return decoder.decode(pngBytes)
        }

        private fun cacheTtlMs(positive: Boolean): Long = if (positive) POSITIVE_CACHE_MS else NEGATIVE_CACHE_MS

        companion object {
            private const val TAG = "MCP:RootScreenCapture"

            /** Root, once granted, does not disappear; re-probe rarely to catch a revoke. */
            private const val POSITIVE_CACHE_MS = 60_000L

            /** A denied/absent root may be granted at any time; retry the probe soon. */
            private const val NEGATIVE_CACHE_MS = 5_000L
        }
    }

/** PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A. */
internal fun isPng(bytes: ByteArray): Boolean {
    val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    if (bytes.size <= signature.size) return false
    return signature.indices.all { bytes[it] == signature[it] }
}

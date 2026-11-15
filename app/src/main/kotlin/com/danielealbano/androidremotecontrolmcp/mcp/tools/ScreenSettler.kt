package com.danielealbano.androidremotecontrolmcp.mcp.tools

import android.os.SystemClock
import android.util.Log
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityServiceProvider
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityTreeLock
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Generates a [TreeFingerprint] of the current UI by walking the raw
 * [android.view.accessibility.AccessibilityNodeInfo] trees of all windows directly — no parsed
 * data objects, no node cache population. Shared by [WaitForIdleTool] and [ScreenSettler].
 *
 * The clear+read critical section is serialized on [AccessibilityTreeLock] against concurrent
 * MCP requests: a parallel request's cache clear must not wipe the framework tree mid-traversal
 * of this fingerprint read.
 *
 * @throws McpToolException.PermissionDenied if the accessibility service is not ready.
 * @throws McpToolException.ActionFailed if no window has a root node.
 */
internal fun generateCurrentTreeFingerprint(
    accessibilityServiceProvider: AccessibilityServiceProvider,
    treeFingerprint: TreeFingerprint,
): IntArray =
    synchronized(AccessibilityTreeLock.monitor) {
        generateCurrentTreeFingerprintLocked(accessibilityServiceProvider, treeFingerprint)
    }

@Suppress("ThrowsCount")
private fun generateCurrentTreeFingerprintLocked(
    accessibilityServiceProvider: AccessibilityServiceProvider,
    treeFingerprint: TreeFingerprint,
): IntArray {
    if (!accessibilityServiceProvider.isReady()) {
        throw McpToolException.PermissionDenied(
            "Accessibility service not enabled or not ready. " +
                "Please enable it in Android Settings > Accessibility.",
        )
    }

    // Drop the framework's accessibility node cache before fingerprinting. Chromium WebView
    // suppresses/throttles the content-changed events that invalidate that cache, so without
    // this the fingerprint would be computed from a stale tree that never reflects ongoing
    // JavaScript DOM changes — reporting the UI as idle while it is still mutating. See
    // AccessibilityServiceProvider.clearFrameworkNodeCache.
    accessibilityServiceProvider.clearFrameworkNodeCache()

    val accessibilityWindows = accessibilityServiceProvider.getAccessibilityWindows()

    if (accessibilityWindows.isNotEmpty()) {
        try {
            val rootNodes =
                accessibilityWindows.mapNotNull { window ->
                    window.root
                }
            if (rootNodes.isEmpty()) {
                throw McpToolException.ActionFailed("All windows returned null root nodes.")
            }
            return treeFingerprint.generateFromRawNodes(rootNodes)
        } finally {
            for (w in accessibilityWindows) {
                @Suppress("DEPRECATION")
                w.recycle()
            }
        }
    }

    // Fallback to single root node
    val rootNode =
        accessibilityServiceProvider.getRootNode()
            ?: throw McpToolException.ActionFailed(
                "No windows available and no active window root node. " +
                    "The screen may be transitioning.",
            )
    return treeFingerprint.generateFromRawNodes(listOf(rootNode))
}

/**
 * Waits until the UI stops changing ("settles"), using the same fingerprint polling as
 * wait_for_idle: the UI is settled when two consecutive fingerprints, taken
 * [IDLE_CHECK_INTERVAL_MS] apart, match exactly.
 *
 * Unlike wait_for_idle this never fails on timeout: it returns false and the caller proceeds
 * with the current state. Only [McpToolException.PermissionDenied] propagates (the caller's
 * own read would fail with the same error anyway); transient tree-read failures during a
 * transition just reset the idle counter.
 */
class ScreenSettler
    @Inject
    constructor(
        private val accessibilityServiceProvider: AccessibilityServiceProvider,
    ) {
        private val treeFingerprint = TreeFingerprint()

        /** Returns true when the UI settled within [timeoutMs], false on timeout. */
        @Suppress("ReturnCount", "NestedBlockDepth")
        suspend fun awaitIdle(timeoutMs: Long): Boolean {
            if (timeoutMs <= 0) return false
            val startTime = SystemClock.elapsedRealtime()
            var previousFingerprint: IntArray? = null
            var consecutiveIdleChecks = 0

            while (SystemClock.elapsedRealtime() - startTime < timeoutMs) {
                try {
                    val currentFingerprint =
                        generateCurrentTreeFingerprint(accessibilityServiceProvider, treeFingerprint)

                    if (previousFingerprint != null) {
                        val similarity = treeFingerprint.compare(previousFingerprint, currentFingerprint)
                        if (similarity >= TreeFingerprint.FULL_MATCH_PERCENTAGE) {
                            consecutiveIdleChecks++
                            if (consecutiveIdleChecks >= REQUIRED_IDLE_CHECKS) {
                                val elapsed = SystemClock.elapsedRealtime() - startTime
                                Log.d(TAG, "settle: UI idle after ${elapsed}ms")
                                return true
                            }
                        } else {
                            consecutiveIdleChecks = 0
                        }
                    }
                    previousFingerprint = currentFingerprint
                } catch (e: McpToolException) {
                    if (e is McpToolException.PermissionDenied) throw e
                    // Tree parse failures during transitions — reset idle counter
                    consecutiveIdleChecks = 0
                    previousFingerprint = null
                }

                delay(IDLE_CHECK_INTERVAL_MS)
            }

            Log.d(TAG, "settle: UI did not settle within ${timeoutMs}ms, continuing anyway")
            return false
        }

        companion object {
            private const val TAG = "MCP:ScreenSettler"
            private const val IDLE_CHECK_INTERVAL_MS = 250L

            /** Number of consecutive matching fingerprints required to consider the UI settled. */
            private const val REQUIRED_IDLE_CHECKS = 2
        }
    }

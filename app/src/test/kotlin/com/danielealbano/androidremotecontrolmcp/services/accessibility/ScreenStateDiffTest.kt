package com.danielealbano.androidremotecontrolmcp.services.accessibility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ScreenStateDiff")
class ScreenStateDiffTest {
    private val formatter = CompactTreeFormatter()

    private val screenInfo =
        ScreenInfo(
            width = 1080,
            height = 2400,
            densityDpi = 420,
            orientation = "portrait",
        )

    private fun node(
        id: String,
        text: String? = null,
        actions: List<String> = emptyList(),
        visible: Boolean = true,
        children: List<AccessibilityNodeData> = emptyList(),
    ) = AccessibilityNodeData(
        id = id,
        className = "android.widget.TextView",
        text = text,
        bounds = BoundsData(0, 0, 100, 50),
        enabled = true,
        visible = visible,
        actions = actions,
        children = children,
    )

    private fun rootOf(children: List<AccessibilityNodeData>) =
        AccessibilityNodeData(
            id = "node_root",
            className = "android.widget.FrameLayout",
            resourceId = "com.example:id/root",
            bounds = BoundsData(0, 0, 1080, 2400),
            enabled = true,
            visible = true,
            children = children,
        )

    private fun resultOf(tree: AccessibilityNodeData) =
        MultiWindowResult(
            windows =
                listOf(
                    WindowData(
                        windowId = 1,
                        windowType = "APPLICATION",
                        packageName = "com.example",
                        title = "Test",
                        focused = true,
                        tree = tree,
                    ),
                ),
        )

    private fun rowsOf(
        tree: AccessibilityNodeData,
        options: ScreenStateFormatOptions = ScreenStateFormatOptions(),
    ) = formatter.collectScreenStateRows(resultOf(tree), options)

    @Nested
    @DisplayName("collectScreenStateRows")
    inner class CollectTests {
        @Test
        @DisplayName("lists kept nodes keyed by id in capture order")
        fun listsKeptNodesKeyedByIdInCaptureOrder() {
            val tree =
                rootOf(
                    listOf(
                        node("node_a", text = "A"),
                        node("node_b", text = "B"),
                    ),
                )

            val rows = rowsOf(tree)

            assertEquals(listOf("node_root", "node_a", "node_b"), rows.entries.keys.toList())
            assertTrue(
                rows.entries
                    .getValue("node_a")
                    .row
                    .startsWith("node_a\tTextView\tA\t"),
            )
        }

        @Test
        @DisplayName("skips structural-only nodes but keeps their listed children")
        fun skipsStructuralOnlyNodes() {
            val wrapper =
                AccessibilityNodeData(
                    id = "node_wrapper",
                    className = "android.widget.FrameLayout",
                    bounds = BoundsData(0, 0, 100, 100),
                    children = listOf(node("node_inner", text = "inner")),
                )
            val tree = rootOf(listOf(wrapper))

            val rows = rowsOf(tree)

            assertEquals(listOf("node_root", "node_inner"), rows.entries.keys.toList())
        }

        @Test
        @DisplayName("include_offscreen=false drops off-screen nodes")
        fun includeOffscreenFalseDropsOffscreenNodes() {
            val tree =
                rootOf(
                    listOf(
                        node("node_on", text = "on", visible = true),
                        node("node_off", text = "off", visible = false),
                    ),
                )

            val rows = rowsOf(tree, ScreenStateFormatOptions(includeOffscreen = false))

            assertEquals(listOf("node_root", "node_on"), rows.entries.keys.toList())
        }
    }

    @Nested
    @DisplayName("computeScreenStateDiff")
    inner class ComputeTests {
        @Test
        @DisplayName("classifies added, changed and removed nodes by id")
        fun classifiesAddedChangedRemoved() {
            val unchanged = (1..6).map { node("node_same_$it", text = "same$it") }
            val baseline =
                rowsOf(
                    rootOf(
                        unchanged +
                            listOf(
                                node("node_changed", text = "before"),
                                node("node_removed", text = "gone"),
                            ),
                    ),
                )
            val current =
                rowsOf(
                    rootOf(
                        unchanged +
                            listOf(
                                node("node_changed", text = "after"),
                                node("node_added", text = "new"),
                            ),
                    ),
                )

            val diff = computeScreenStateDiff(baseline, current)

            assertEquals(listOf("node_added"), diff.added.map { it.id })
            assertEquals(listOf("node_changed"), diff.changed.map { it.id })
            assertEquals(listOf("node_removed"), diff.removed.map { it.id })
            assertEquals(9, diff.baselineNodeCount)
            assertEquals(9, diff.currentNodeCount)
            assertEquals(3, diff.changeCount)
            assertFalse(diff.coversMostOfTree)
        }

        @Test
        @DisplayName("an actions-only change counts as changed")
        fun actionsOnlyChangeCountsAsChanged() {
            val baseline = rowsOf(rootOf(listOf(node("node_vote", text = "post"))))
            val current =
                rowsOf(rootOf(listOf(node("node_vote", text = "post", actions = listOf("Upvote")))))

            val diff = computeScreenStateDiff(baseline, current)

            assertEquals(listOf("node_vote"), diff.changed.map { it.id })
            assertEquals(0, diff.added.size)
            assertEquals(0, diff.removed.size)
        }

        @Test
        @DisplayName("identical captures produce an empty diff")
        fun identicalCapturesProduceEmptyDiff() {
            val tree = rootOf(listOf(node("node_a", text = "A")))

            val diff = computeScreenStateDiff(rowsOf(tree), rowsOf(tree))

            assertEquals(0, diff.changeCount)
            assertFalse(diff.coversMostOfTree)
        }

        @Test
        @DisplayName("coversMostOfTree when changes outnumber half of the larger side")
        fun coversMostOfTreeThreshold() {
            val baseline =
                rowsOf(
                    rootOf(
                        listOf(
                            node("node_1", text = "1"),
                            node("node_2", text = "2"),
                            node("node_3", text = "3"),
                        ),
                    ),
                )
            // root + 3 changed of 4 nodes per side: 3 changes, 3*2 > 4 -> covers most.
            val mostlyChanged =
                rowsOf(
                    rootOf(
                        listOf(
                            node("node_1", text = "1!"),
                            node("node_2", text = "2!"),
                            node("node_3", text = "3!"),
                        ),
                    ),
                )
            // root + 1 changed of 4: 1 change, 1*2 <= 4 -> does not cover most.
            val slightlyChanged =
                rowsOf(
                    rootOf(
                        listOf(
                            node("node_1", text = "1!"),
                            node("node_2", text = "2"),
                            node("node_3", text = "3"),
                        ),
                    ),
                )

            assertTrue(computeScreenStateDiff(baseline, mostlyChanged).coversMostOfTree)
            assertFalse(computeScreenStateDiff(baseline, slightlyChanged).coversMostOfTree)
        }
    }

    @Nested
    @DisplayName("formatScreenStateDiff")
    inner class FormatTests {
        @Test
        @DisplayName("renders markers, counts and the changed rows only")
        fun rendersMarkersCountsAndChangedRows() {
            val baseline =
                rowsOf(
                    rootOf(
                        listOf(
                            node("node_same", text = "same"),
                            node("node_changed", text = "before"),
                            node("node_removed", text = "gone"),
                        ),
                    ),
                )
            val current =
                rowsOf(
                    rootOf(
                        listOf(
                            node("node_same", text = "same"),
                            node("node_changed", text = "after"),
                            node("node_added", text = "new"),
                        ),
                    ),
                )

            val text = formatScreenStateDiff(computeScreenStateDiff(baseline, current), screenInfo)

            assertTrue(text.contains("+1 added, ~1 changed, -1 removed"), text)
            assertTrue(text.lines().any { it.startsWith("+node_added\t") }, text)
            assertTrue(text.lines().any { it.startsWith("~node_changed\t") && it.contains("after") }, text)
            assertTrue(text.lines().any { it.startsWith("-node_removed\t") && it.contains("gone") }, text)
            assertFalse(text.lines().any { it.contains("node_same") }, text)
            assertFalse(text.contains("hierarchy:"), text)
        }

        @Test
        @DisplayName("renders the actions section for added nodes with actions")
        fun rendersActionsSectionForAddedNodes() {
            val baseline = rowsOf(rootOf(listOf(node("node_a", text = "A"))))
            val current =
                rowsOf(
                    rootOf(
                        listOf(
                            node("node_a", text = "A"),
                            node("node_vote", text = "post", actions = listOf("Upvote", "Downvote")),
                        ),
                    ),
                )

            val text = formatScreenStateDiff(computeScreenStateDiff(baseline, current), screenInfo)

            assertTrue(text.contains(CompactTreeFormatter.ACTIONS_HEADER), text)
            assertTrue(text.contains("* node_vote: Upvote¦Downvote"), text)
        }

        @Test
        @DisplayName("empty diff renders the no-changes note and the screen line only")
        fun emptyDiffRendersNoChangesNote() {
            val tree = rootOf(listOf(node("node_a", text = "A")))
            val diff = computeScreenStateDiff(rowsOf(tree), rowsOf(tree))

            val text = formatScreenStateDiff(diff, screenInfo)

            assertEquals(
                "note:no changes since the previous get_screen_state capture (2 nodes)\n" +
                    "screen:1080x2400 density:420 orientation:portrait",
                text,
            )
        }
    }

    @Nested
    @DisplayName("ScreenStateDiffStoreImpl")
    inner class StoreTests {
        @Test
        @DisplayName("replace returns the previous baseline and clear drops it")
        fun replaceReturnsPreviousBaseline() {
            val store = ScreenStateDiffStoreImpl()
            val first = rowsOf(rootOf(listOf(node("node_a", text = "A"))))
            val second = rowsOf(rootOf(listOf(node("node_a", text = "B"))))

            assertEquals(null, store.replace(first))
            assertEquals(first, store.replace(second))
            store.clear()
            assertEquals(null, store.replace(first))
        }
    }
}

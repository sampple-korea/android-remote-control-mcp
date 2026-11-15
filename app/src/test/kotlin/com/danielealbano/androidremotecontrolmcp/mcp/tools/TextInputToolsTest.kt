@file:Suppress("DEPRECATION", "TooManyFunctions", "LargeClass")

package com.danielealbano.androidremotecontrolmcp.mcp.tools

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.danielealbano.androidremotecontrolmcp.data.model.ServerLogEntry
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityNodeCache
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityNodeData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityServiceProvider
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityTreeParser
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ActionExecutor
import com.danielealbano.androidremotecontrolmcp.services.accessibility.BoundsData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.CachedNode
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ElementFinder
import com.danielealbano.androidremotecontrolmcp.testutil.PrivacyToolTestDoubles
import com.danielealbano.androidremotecontrolmcp.testutil.RecordingServerLogRepository
import com.danielealbano.androidremotecontrolmcp.testutil.readNodesOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("TextInputTools")
class TextInputToolsTest {
    private val mockTreeParser = mockk<AccessibilityTreeParser>()
    private val mockActionExecutor = mockk<ActionExecutor>()
    private val mockAccessibilityServiceProvider = mockk<AccessibilityServiceProvider>()
    private val mockNodeCache = mockk<AccessibilityNodeCache>(relaxed = true)
    private val mockRootNode = mockk<AccessibilityNodeInfo>()
    private val mockWindowInfo = mockk<AccessibilityWindowInfo>()
    private val serverLog = RecordingServerLogRepository()

    /** The node references, by id, that every tree read returns with its tree (see [readsTree]). */
    private val readNodes = mutableMapOf<String, CachedNode>()
    private val targetResolver =
        NodeTargetResolver(
            ElementFinder(),
            PrivacyToolTestDoubles.passthroughGate(),
            PrivacyToolTestDoubles.identitySubstitutor(),
        )

    /** The text the screen's field currently holds; tree reads report it and a set text replaces it. */
    private var fieldText: String? = ""

    private fun fieldNode(text: String?) =
        AccessibilityNodeData(
            id = "node_edit",
            className = "android.widget.EditText",
            text = text,
            bounds = BoundsData(0, 100, 1080, 200),
            editable = true,
            visible = true,
        )

    /** A screen holding the type tools' usual target, the editable field "node_edit" with [fieldText]. */
    private fun currentTree() =
        AccessibilityNodeData(
            id = "node_root",
            className = "android.widget.FrameLayout",
            bounds = BoundsData(0, 0, 1080, 2400),
            visible = true,
            children = listOf(fieldNode(fieldText)),
        )

    private fun appendTool() =
        EditTextTool(
            mockTreeParser,
            mockActionExecutor,
            mockAccessibilityServiceProvider,
            mockNodeCache,
            PrivacyToolTestDoubles.passthroughGate(),
            PrivacyToolTestDoubles.identitySubstitutor(),
            targetResolver,
            serverLog,
        )

    private fun insertTool() =
        EditTextTool(
            mockTreeParser,
            mockActionExecutor,
            mockAccessibilityServiceProvider,
            mockNodeCache,
            PrivacyToolTestDoubles.passthroughGate(),
            PrivacyToolTestDoubles.identitySubstitutor(),
            targetResolver,
            serverLog,
        )

    private fun replaceTool() =
        EditTextTool(
            mockTreeParser,
            mockActionExecutor,
            mockAccessibilityServiceProvider,
            mockNodeCache,
            PrivacyToolTestDoubles.passthroughGate(),
            PrivacyToolTestDoubles.identitySubstitutor(),
            targetResolver,
            serverLog,
        )

    private fun clearTool() =
        EditTextTool(
            mockTreeParser,
            mockActionExecutor,
            mockAccessibilityServiceProvider,
            mockNodeCache,
            PrivacyToolTestDoubles.passthroughGate(),
            PrivacyToolTestDoubles.identitySubstitutor(),
            targetResolver,
            serverLog,
        )

    private fun extractTextContent(result: CallToolResult): String {
        assertEquals(1, result.content.size)
        val textContent = result.content[0] as TextContent
        return textContent.text
    }

    /** Every tree read parses to the current tree and returns the [readNodes] of its nodes as references. */
    private fun readsTree() {
        every { mockTreeParser.parseTree(mockRootNode, "root_w0", any()) } answers {
            val tree = currentTree()
            thirdArg<MutableMap<String, CachedNode>>().putAll(readNodesOf(tree, readNodes))
            tree
        }
    }

    /** Tree reads parse to a fixed [tree] instead of the current field tree. */
    private fun readsFixedTree(tree: AccessibilityNodeData) {
        every { mockTreeParser.parseTree(mockRootNode, "root_w0", any()) } answers {
            thirdArg<MutableMap<String, CachedNode>>().putAll(readNodesOf(tree, readNodes))
            tree
        }
    }

    @BeforeEach
    fun setUp() {
        fieldText = ""
        every { mockAccessibilityServiceProvider.isReady() } returns true
        every { mockAccessibilityServiceProvider.clearFrameworkNodeCache() } returns Unit
        every { mockWindowInfo.id } returns 0
        every { mockWindowInfo.root } returns mockRootNode
        every { mockWindowInfo.type } returns AccessibilityWindowInfo.TYPE_APPLICATION
        every { mockWindowInfo.title } returns "Test"
        every { mockWindowInfo.layer } returns 0
        every { mockWindowInfo.isFocused } returns true
        every { mockWindowInfo.recycle() } returns Unit
        every { mockRootNode.refresh() } returns true
        every { mockRootNode.packageName } returns "com.example"
        every { mockRootNode.recycle() } returns Unit
        every { mockRootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } returns null
        every {
            mockAccessibilityServiceProvider.getAccessibilityWindows()
        } returns listOf(mockWindowInfo)
        every { mockAccessibilityServiceProvider.getCurrentPackageName() } returns "com.example"
        every { mockAccessibilityServiceProvider.getCurrentActivityName() } returns ".Main"
        readsTree()
        // A set text lands on the field: later reads report the new text, like the real field would.
        coEvery { mockActionExecutor.setTextOnNode(any(), any(), any()) } answers {
            fieldText = secondArg()
            Result.success(Unit)
        }
        coEvery { mockActionExecutor.clickNode(any(), any()) } returns Result.success(Unit)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Nested
    @DisplayName("TextEditCalculator")
    inner class TextEditCalculatorTests {
        @Test
        fun `appended concatenates at the end`() {
            assertEquals("Hello World", TextEditCalculator.appended("Hello", " World"))
            assertEquals("x", TextEditCalculator.appended("", "x"))
            assertEquals("x", TextEditCalculator.appended("x", ""))
        }

        @Test
        fun `inserted puts the text at the offset`() {
            assertEquals("Hel Worldlo", TextEditCalculator.inserted("Hello", 3, " World"))
            assertEquals("xHello", TextEditCalculator.inserted("Hello", 0, "x"))
            assertEquals("Hellox", TextEditCalculator.inserted("Hello", 5, "x"))
            assertEquals("x", TextEditCalculator.inserted("", 0, "x"))
        }

        @Test
        fun `inserted counts offsets in UTF-16 code units`() {
            // "a😀b" is 4 UTF-16 units: offset 3 is right after the emoji.
            assertEquals("a😀Xb", TextEditCalculator.inserted("a😀b", 3, "X"))
        }

        @Test
        fun `inserted rejects an out of range offset`() {
            assertThrows<IllegalArgumentException> { TextEditCalculator.inserted("Hello", 6, "x") }
            assertThrows<IllegalArgumentException> { TextEditCalculator.inserted("Hello", -1, "x") }
        }

        @Test
        fun `replaced replaces the first occurrence only`() {
            assertEquals("baz bar foo", TextEditCalculator.replaced("foo bar foo", "foo", "baz"))
            assertEquals("Hello World", TextEditCalculator.replaced("Hello World", "World", "World"))
        }

        @Test
        fun `replaced with an empty replacement deletes the occurrence`() {
            assertEquals(" World", TextEditCalculator.replaced("Hello World", "Hello", ""))
        }

        @Test
        fun `replaced returns null when the search text is absent`() {
            assertEquals(null, TextEditCalculator.replaced("Hello", "xyz", "a"))
            assertEquals(null, TextEditCalculator.replaced("", "xyz", "a"))
        }

        @Test
        fun `replaced sets the exact computed value for the settings limit regression`() {
            // The key-event implementation corrupted this very edit ("1202" -> "120" came back as
            // "120022222022000502022"); the computed set must produce exactly "120".
            assertEquals("120", TextEditCalculator.replaced("1202", "1202", "120"))
            assertEquals("302", TextEditCalculator.appended("30", "2"))
        }
    }

    @Nested
    @DisplayName("Typing parameter validation")
    inner class TypingParamsTests {
        @Test
        fun `extractTypingParams returns the defaults`() {
            assertEquals(250 to 50, extractTypingParams(buildJsonObject { }))
            assertEquals(250 to 50, extractTypingParams(null))
        }

        @Test
        fun `extractTypingParams rejects out of range values`() {
            assertThrows<McpToolException.InvalidParams> {
                extractTypingParams(buildJsonObject { put("typing_speed", 5) })
            }
            assertThrows<McpToolException.InvalidParams> {
                extractTypingParams(buildJsonObject { put("typing_speed", 6000) })
            }
            assertThrows<McpToolException.InvalidParams> {
                extractTypingParams(buildJsonObject { put("typing_speed_variance", -1) })
            }
        }

        @Test
        fun `validateTextLength rejects text over the maximum`() {
            validateTextLength("x".repeat(2000))
            val e =
                assertThrows<McpToolException.InvalidParams> {
                    validateTextLength("x".repeat(2001))
                }
            assertTrue(e.message!!.contains("2000"), e.message)
        }
    }

    @Nested
    @DisplayName("guardedTextEdit")
    inner class GuardedTextEditTests {
        @Test
        fun `passes the block result through`() =
            runTest {
                assertEquals("ok", guardedTextEdit("tool", serverLog) { "ok" })
                assertTrue(serverLog.entries.isEmpty())
            }

        @Test
        fun `passes tool exceptions through unchanged and unlogged`() =
            runTest {
                val e =
                    assertThrows<McpToolException.InvalidParams> {
                        guardedTextEdit("tool", serverLog) {
                            throw McpToolException.InvalidParams("bad input")
                        }
                    }
                assertEquals("bad input", e.message)
                assertTrue(serverLog.entries.isEmpty())
            }

        @Test
        fun `converts an unexpected exception into a logged tool failure`() =
            runTest {
                val e =
                    assertThrows<McpToolException.ActionFailed> {
                        guardedTextEdit("edit_text", serverLog) {
                            throw IllegalStateException("framework exploded")
                        }
                    }
                assertTrue(e.message!!.contains("Unexpected error"), e.message)
                val entries = serverLog.ofType(ServerLogEntry.Type.TOOL_CALL)
                assertEquals(1, entries.size)
                assertEquals("edit_text", entries[0].toolName)
                assertTrue(entries[0].message.contains("framework exploded"), entries[0].message)
            }

        @Test
        fun `rethrows cancellation`() =
            runTest {
                assertThrows<CancellationException> {
                    guardedTextEdit("tool", serverLog) { throw CancellationException("cancelled") }
                }
                assertTrue(serverLog.entries.isEmpty())
            }
    }

    @Nested
    @DisplayName("edit_text append")
    inner class EditTextAppendTests {
        @Test
        fun `appends the computed text in one set without clicking the field`() =
            runTest {
                fieldText = "Hello"
                val setText = slot<String>()
                coEvery { mockActionExecutor.setTextOnNode("node_edit", capture(setText), any()) } answers {
                    fieldText = setText.captured
                    Result.success(Unit)
                }

                val result =
                    appendTool().execute(
                        buildJsonObject {
                            put("mode", "append")
                            put("node_id", "node_edit")
                            put("text", " World")
                        },
                    )

                assertEquals("Hello World", setText.captured)
                val text = extractTextContent(result)
                assertTrue(text.contains("Appended 6 characters at end of node 'node_edit'"), text)
                assertTrue(text.contains("Field content: Hello World"), text)
                // The field is never clicked: no focus change, no soft keyboard.
                coVerify(exactly = 0) { mockActionExecutor.clickNode(any(), any()) }
            }

        @Test
        fun `appends to an empty field`() =
            runTest {
                fieldText = null

                val result =
                    appendTool().execute(
                        buildJsonObject {
                            put("mode", "append")
                            put("node_id", "node_edit")
                            put("text", "abc")
                        },
                    )

                assertEquals("abc", fieldText)
                assertTrue(extractTextContent(result).contains("Field content: abc"))
            }

        @Test
        fun `edits the editable field inside a wrapper target`() =
            runTest {
                val wrapper =
                    AccessibilityNodeData(
                        id = "node_root",
                        className = "android.widget.FrameLayout",
                        bounds = BoundsData(0, 0, 1080, 2400),
                        visible = true,
                        children =
                            listOf(
                                AccessibilityNodeData(
                                    id = "node_wrapper",
                                    className = "android.widget.FrameLayout",
                                    bounds = BoundsData(0, 100, 1080, 200),
                                    clickable = true,
                                    visible = true,
                                    children = listOf(fieldNode("inside")),
                                ),
                            ),
                    )
                readsFixedTree(wrapper)
                val setNode = slot<String>()
                val setText = slot<String>()
                coEvery { mockActionExecutor.setTextOnNode(capture(setNode), capture(setText), any()) } returns
                    Result.success(Unit)

                appendTool().execute(
                    buildJsonObject {
                        put("mode", "append")
                        put("node_id", "node_wrapper")
                        put("text", "!")
                    },
                )

                assertEquals("node_edit", setNode.captured)
                assertEquals("inside!", setText.captured)
            }

        @Test
        fun `fails when the target is not editable`() =
            runTest {
                readsFixedTree(
                    AccessibilityNodeData(
                        id = "node_root",
                        className = "android.widget.FrameLayout",
                        bounds = BoundsData(0, 0, 1080, 2400),
                        visible = true,
                        children =
                            listOf(
                                AccessibilityNodeData(
                                    id = "node_label",
                                    className = "android.widget.TextView",
                                    text = "label",
                                    bounds = BoundsData(0, 100, 1080, 200),
                                    visible = true,
                                ),
                            ),
                    ),
                )

                val e =
                    assertThrows<McpToolException.ActionFailed> {
                        appendTool().execute(
                            buildJsonObject {
                                put("mode", "append")
                                put("node_id", "node_label")
                                put("text", "x")
                            },
                        )
                    }
                assertTrue(e.message!!.contains("not an editable text field"), e.message)
                coVerify(exactly = 0) { mockActionExecutor.setTextOnNode(any(), any(), any()) }
            }

        @Test
        fun `fails when the node is not in the tree`() =
            runTest {
                assertThrows<McpToolException.NodeNotFound> {
                    appendTool().execute(
                        buildJsonObject {
                            put("mode", "append")
                            put("node_id", "node_missing")
                            put("text", "x")
                        },
                    )
                }
            }

        @Test
        fun `maps a failed set to a tool failure`() =
            runTest {
                coEvery { mockActionExecutor.setTextOnNode(any(), any(), any()) } returns
                    Result.failure(IllegalStateException("Node 'node_edit' is not editable"))

                val e =
                    assertThrows<McpToolException.ActionFailed> {
                        appendTool().execute(
                            buildJsonObject {
                                put("mode", "append")
                                put("node_id", "node_edit")
                                put("text", "x")
                            },
                        )
                    }
                assertTrue(e.message!!.contains("not editable"), e.message)
            }

        @Test
        fun `returns the candidates when the selector is ambiguous and sets nothing`() =
            runTest {
                readsFixedTree(
                    AccessibilityNodeData(
                        id = "node_root",
                        className = "android.widget.FrameLayout",
                        bounds = BoundsData(0, 0, 1080, 2400),
                        visible = true,
                        children = listOf(fieldNode("a"), fieldNode("b").copy(id = "node_edit_2")),
                    ),
                )

                val result =
                    appendTool().execute(
                        buildJsonObject {
                            put("mode", "append")
                            put("by", "class_name")
                            put("value", "android.widget.EditText")
                            put("exact_match", true)
                            put("text", "x")
                        },
                    )

                assertTrue(extractTextContent(result).contains("Ambiguous selector"), extractTextContent(result))
                coVerify(exactly = 0) { mockActionExecutor.setTextOnNode(any(), any(), any()) }
            }

        @Test
        fun `rejects empty and overlong text and invalid legacy typing parameters`() =
            runTest {
                assertThrows<McpToolException.InvalidParams> {
                    appendTool().execute(
                        buildJsonObject {
                            put("mode", "append")
                            put("node_id", "node_edit")
                            put("text", "")
                        },
                    )
                }
                assertThrows<McpToolException.InvalidParams> {
                    appendTool().execute(
                        buildJsonObject {
                            put("mode", "append")
                            put("node_id", "node_edit")
                            put("text", "x".repeat(2001))
                        },
                    )
                }
                assertThrows<McpToolException.InvalidParams> {
                    appendTool().execute(
                        buildJsonObject {
                            put("mode", "append")
                            put("node_id", "node_edit")
                            put("text", "x")
                            put("typing_speed", 5)
                        },
                    )
                }
            }

        @Test
        fun `an unexpected failure becomes a logged tool error instead of escaping`() =
            runTest {
                every { mockTreeParser.parseTree(mockRootNode, "root_w0", any()) } throws
                    RuntimeException("parser exploded")

                val e =
                    assertThrows<McpToolException.ActionFailed> {
                        appendTool().execute(
                            buildJsonObject {
                                put("mode", "append")
                                put("node_id", "node_edit")
                                put("text", "x")
                            },
                        )
                    }
                assertTrue(e.message!!.contains("Unexpected error"), e.message)
                val entries = serverLog.ofType(ServerLogEntry.Type.TOOL_CALL)
                assertEquals(1, entries.size)
                assertEquals("edit_text", entries[0].toolName)
            }
    }

    @Nested
    @DisplayName("edit_text insert")
    inner class EditTextInsertTests {
        @Test
        fun `inserts the computed text at the offset`() =
            runTest {
                fieldText = "Hello"

                val result =
                    insertTool().execute(
                        buildJsonObject {
                            put("mode", "insert")
                            put("node_id", "node_edit")
                            put("text", " World")
                            put("offset", 3)
                        },
                    )

                assertEquals("Hel Worldlo", fieldText)
                val text = extractTextContent(result)
                assertTrue(text.contains("Inserted 6 characters at offset 3 in node 'node_edit'"), text)
                assertTrue(text.contains("Field content: Hel Worldlo"), text)
                coVerify(exactly = 0) { mockActionExecutor.clickNode(any(), any()) }
            }

        @Test
        fun `inserts at the start and at the end`() =
            runTest {
                fieldText = "mid"
                insertTool().execute(
                    buildJsonObject {
                        put("mode", "insert")
                        put("node_id", "node_edit")
                        put("text", "pre-")
                        put("offset", 0)
                    },
                )
                assertEquals("pre-mid", fieldText)

                insertTool().execute(
                    buildJsonObject {
                        put("mode", "insert")
                        put("node_id", "node_edit")
                        put("text", "-post")
                        put("offset", 7)
                    },
                )
                assertEquals("pre-mid-post", fieldText)
            }

        @Test
        fun `rejects an offset past the end of the current text`() =
            runTest {
                fieldText = "Hello"

                val e =
                    assertThrows<McpToolException.InvalidParams> {
                        insertTool().execute(
                            buildJsonObject {
                                put("mode", "insert")
                                put("node_id", "node_edit")
                                put("text", "x")
                                put("offset", 6)
                            },
                        )
                    }
                assertTrue(e.message!!.contains("offset (6) exceeds text length (5)"), e.message)
                coVerify(exactly = 0) { mockActionExecutor.setTextOnNode(any(), any(), any()) }
            }

        @Test
        fun `rejects a negative offset`() =
            runTest {
                assertThrows<McpToolException.InvalidParams> {
                    insertTool().execute(
                        buildJsonObject {
                            put("mode", "insert")
                            put("node_id", "node_edit")
                            put("text", "x")
                            put("offset", -1)
                        },
                    )
                }
            }
    }

    @Nested
    @DisplayName("edit_text replace")
    inner class EditTextReplaceTests {
        @Test
        fun `replaces the first occurrence with the computed text`() =
            runTest {
                fieldText = "foo bar foo"

                val result =
                    replaceTool().execute(
                        buildJsonObject {
                            put("mode", "replace")
                            put("node_id", "node_edit")
                            put("search", "foo")
                            put("new_text", "baz")
                        },
                    )

                assertEquals("baz bar foo", fieldText)
                val text = extractTextContent(result)
                assertTrue(text.contains("Replaced 3 characters with 3 characters in node 'node_edit'"), text)
                assertTrue(text.contains("Field content: baz bar foo"), text)
                coVerify(exactly = 0) { mockActionExecutor.clickNode(any(), any()) }
            }

        @Test
        fun `sets exactly the computed value for the settings limit regression`() =
            runTest {
                // Regression: the key-event implementation turned this edit into
                // "120022222022000502022"; the computed set must land exactly on "120".
                fieldText = "1202"

                replaceTool().execute(
                    buildJsonObject {
                        put("mode", "replace")
                        put("node_id", "node_edit")
                        put("search", "1202")
                        put("new_text", "120")
                    },
                )

                assertEquals("120", fieldText)
            }

        @Test
        fun `an empty replacement deletes the found text`() =
            runTest {
                fieldText = "Hello World"

                replaceTool().execute(
                    buildJsonObject {
                        put("mode", "replace")
                        put("node_id", "node_edit")
                        put("search", "Hello")
                        put("new_text", "")
                    },
                )

                assertEquals(" World", fieldText)
            }

        @Test
        fun `fails when the search text is not in the field`() =
            runTest {
                fieldText = "Something"

                val e =
                    assertThrows<McpToolException.NodeNotFound> {
                        replaceTool().execute(
                            buildJsonObject {
                                put("mode", "replace")
                                put("node_id", "node_edit")
                                put("search", "NotFound")
                                put("new_text", "X")
                            },
                        )
                    }
                assertTrue(e.message!!.contains("not found in node 'node_edit'"), e.message)
                coVerify(exactly = 0) { mockActionExecutor.setTextOnNode(any(), any(), any()) }
            }

        @Test
        fun `rejects an empty search and an overlong new text`() =
            runTest {
                assertThrows<McpToolException.InvalidParams> {
                    replaceTool().execute(
                        buildJsonObject {
                            put("mode", "replace")
                            put("node_id", "node_edit")
                            put("search", "")
                            put("new_text", "X")
                        },
                    )
                }
                assertThrows<McpToolException.InvalidParams> {
                    replaceTool().execute(
                        buildJsonObject {
                            put("mode", "replace")
                            put("node_id", "node_edit")
                            put("search", "a")
                            put("new_text", "x".repeat(2001))
                        },
                    )
                }
            }
    }

    @Nested
    @DisplayName("edit_text clear")
    inner class EditTextClearTests {
        @Test
        fun `clears the field with one empty set`() =
            runTest {
                fieldText = "Hello"

                val result =
                    clearTool().execute(
                        buildJsonObject {
                            put("mode", "clear")
                            put("node_id", "node_edit")
                        },
                    )

                assertEquals("", fieldText)
                val text = extractTextContent(result)
                assertTrue(text.contains("Text cleared from node 'node_edit'"), text)
                assertTrue(text.endsWith("Field content: "), text)
                coVerify(exactly = 0) { mockActionExecutor.clickNode(any(), any()) }
            }

        @Test
        fun `an already empty field succeeds without setting anything`() =
            runTest {
                fieldText = ""

                val result =
                    clearTool().execute(
                        buildJsonObject {
                            put("mode", "clear")
                            put("node_id", "node_edit")
                        },
                    )

                assertTrue(extractTextContent(result).contains("Text cleared from node 'node_edit'"))
                coVerify(exactly = 0) { mockActionExecutor.setTextOnNode(any(), any(), any()) }
            }

        @Test
        fun `fails when the field reads back unchanged`() =
            runTest {
                fieldText = "Hello"
                // The set reports success but the text stays, like a field that dropped the change.
                coEvery { mockActionExecutor.setTextOnNode(any(), any(), any()) } returns Result.success(Unit)

                val e =
                    assertThrows<McpToolException.ActionFailed> {
                        clearTool().execute(
                            buildJsonObject {
                                put("mode", "clear")
                                put("node_id", "node_edit")
                            },
                        )
                    }
                assertTrue(e.message!!.contains("was not deleted"), e.message)
            }

        @Test
        fun `succeeds when the field fills itself again after the clear`() =
            runTest {
                fieldText = "Hello"
                coEvery { mockActionExecutor.setTextOnNode(any(), any(), any()) } answers {
                    fieldText = if (secondArg<String>().isEmpty()) "refill" else secondArg()
                    Result.success(Unit)
                }

                val result =
                    clearTool().execute(
                        buildJsonObject {
                            put("mode", "clear")
                            put("node_id", "node_edit")
                        },
                    )

                assertTrue(extractTextContent(result).contains("Field content: refill"), extractTextContent(result))
            }
    }

    @Nested
    @DisplayName("press_key")
    inner class PressKeyToolTests {
        private fun pressKeyTool() = PressKeyTool(mockAccessibilityServiceProvider)

        @Test
        fun `BACK is rejected - the dedicated press_back tool handles it`() =
            runTest {
                assertThrows<McpToolException.InvalidParams> {
                    pressKeyTool().execute(buildJsonObject { put("key", "BACK") })
                }
            }

        @Test
        fun `rejects an unknown key`() =
            runTest {
                assertThrows<McpToolException.InvalidParams> {
                    pressKeyTool().execute(buildJsonObject { put("key", "F1") })
                }
            }

        @Test
        fun `ENTER fails when no editable node is focused`() =
            runTest {
                assertThrows<McpToolException.NodeNotFound> {
                    pressKeyTool().execute(buildJsonObject { put("key", "ENTER") })
                }
            }
    }
}

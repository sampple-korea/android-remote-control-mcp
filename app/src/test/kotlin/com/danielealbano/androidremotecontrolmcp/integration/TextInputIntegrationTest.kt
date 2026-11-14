@file:Suppress("DEPRECATION")

package com.danielealbano.androidremotecontrolmcp.integration

import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityNodeData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.BoundsData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.CachedNode
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ElementInfo
import com.danielealbano.androidremotecontrolmcp.services.accessibility.FindBy
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScreenInfo
import com.danielealbano.androidremotecontrolmcp.services.accessibility.WindowData
import com.danielealbano.androidremotecontrolmcp.testutil.readNodesOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Text Input Integration Tests")
class TextInputIntegrationTest {
    private val sampleScreenInfo =
        ScreenInfo(
            width = 1080,
            height = 2400,
            densityDpi = 420,
            orientation = "portrait",
        )

    /** A screen whose editable field "node_edit" holds [text]. */
    private fun treeWith(text: String?) =
        AccessibilityNodeData(
            id = "node_root",
            className = "android.widget.FrameLayout",
            bounds = BoundsData(0, 0, 1080, 2400),
            visible = true,
            children =
                listOf(
                    AccessibilityNodeData(
                        id = "node_edit",
                        className = "android.widget.EditText",
                        text = text,
                        bounds = BoundsData(50, 800, 500, 900),
                        editable = true,
                        focusable = true,
                        enabled = true,
                        visible = true,
                    ),
                ),
        )

    /**
     * Makes [deps]' tree reads report [initial] as the field's text and applies setTextOnNode calls to it,
     * so a later read sees the set text like the real field would show. With [loseSets] the set reports
     * success but the text stays, like a field that dropped the change. Returns the field's current text.
     */
    private fun MockDependencies.wireField(
        initial: String?,
        loseSets: Boolean = false,
    ): () -> String? {
        var current = initial
        every { treeParser.parseTree(any(), "root_w0", any()) } answers {
            val tree = treeWith(current)
            thirdArg<MutableMap<String, CachedNode>>().putAll(readNodesOf(tree, readNodes))
            tree
        }
        coEvery { actionExecutor.setTextOnNode("node_edit", any(), any()) } answers {
            if (!loseSets) current = secondArg()
            Result.success(Unit)
        }
        return { current }
    }

    private fun depsWithField(
        initial: String?,
        loseSets: Boolean = false,
    ): Pair<MockDependencies, () -> String?> {
        val deps = McpIntegrationTestHelper.createMockDependencies()
        McpIntegrationTestHelper.setupMultiWindowMock(deps, treeWith(initial), sampleScreenInfo)
        val current = deps.wireField(initial, loseSets)
        return deps to current
    }

    @BeforeEach
    fun setUp() {
        McpIntegrationTestHelper.mockAndroidLog()
    }

    @AfterEach
    fun tearDown() {
        McpIntegrationTestHelper.unmockAndroidLog()
    }

    @Test
    fun `type_append_text with node_id returns success with field content`() =
        runTest {
            val (deps, fieldText) = depsWithField("existing")

            McpIntegrationTestHelper.withTestApplication(deps) { client, _ ->
                val result =
                    client.callTool(
                        name = "android_type_append_text",
                        arguments = mapOf("node_id" to "node_edit", "text" to "Hello"),
                    )
                assertNotEquals(true, result.isError)
                val text = (result.content[0] as TextContent).text
                assertTrue(text.contains("Appended 5 characters"), text)
                assertTrue(text.contains("Field content: existingHello"), text)
            }
            assertEquals("existingHello", fieldText())
            // The field is never clicked: the set-text flow shows no soft keyboard.
            coVerify(exactly = 0) { deps.actionExecutor.clickNode(any(), any<List<WindowData>>()) }
        }

    @Test
    fun `type_append_text with missing text returns error`() =
        runTest {
            McpIntegrationTestHelper.withTestApplication { client, _ ->
                val result =
                    client.callTool(
                        name = "android_type_append_text",
                        arguments = emptyMap(),
                    )
                assertEquals(true, result.isError)
                val text = (result.content[0] as TextContent).text
                assertTrue(text.isNotEmpty())
            }
        }

    @Test
    fun `type_insert_text with valid offset returns success with field content`() =
        runTest {
            val (deps, fieldText) = depsWithField("Hello")

            McpIntegrationTestHelper.withTestApplication(deps) { client, _ ->
                val result =
                    client.callTool(
                        name = "android_type_insert_text",
                        arguments =
                            mapOf(
                                "node_id" to "node_edit",
                                "text" to " World",
                                "offset" to 3,
                            ),
                    )
                assertNotEquals(true, result.isError)
                val text = (result.content[0] as TextContent).text
                assertTrue(text.contains("Field content: Hel Worldlo"), text)
            }
            assertEquals("Hel Worldlo", fieldText())
        }

    @Test
    fun `type_replace_text with found search text returns success with field content`() =
        runTest {
            val (deps, fieldText) = depsWithField("Hello World")

            McpIntegrationTestHelper.withTestApplication(deps) { client, _ ->
                val result =
                    client.callTool(
                        name = "android_type_replace_text",
                        arguments =
                            mapOf(
                                "node_id" to "node_edit",
                                "search" to "Hello",
                                "new_text" to "Goodbye",
                            ),
                    )
                assertNotEquals(true, result.isError)
                val text = (result.content[0] as TextContent).text
                assertTrue(text.contains("Field content: Goodbye World"), text)
            }
            assertEquals("Goodbye World", fieldText())
        }

    @Test
    fun `type_replace_text with missing search text returns error`() =
        runTest {
            val (deps, _) = depsWithField("Something")

            McpIntegrationTestHelper.withTestApplication(deps) { client, _ ->
                val result =
                    client.callTool(
                        name = "android_type_replace_text",
                        arguments =
                            mapOf(
                                "node_id" to "node_edit",
                                "search" to "NotFound",
                                "new_text" to "X",
                            ),
                    )
                assertEquals(true, result.isError)
            }
        }

    @Test
    fun `type_clear_text returns success with field content`() =
        runTest {
            val (deps, fieldText) = depsWithField("Hello")

            McpIntegrationTestHelper.withTestApplication(deps) { client, _ ->
                val result =
                    client.callTool(
                        name = "android_type_clear_text",
                        arguments = mapOf("node_id" to "node_edit"),
                    )
                assertNotEquals(true, result.isError)
                val text = (result.content[0] as TextContent).text
                assertTrue(text.endsWith("Field content: "), text)
            }
            assertEquals("", fieldText())
        }

    @Test
    fun `type_clear_text returns an error when the field is not cleared`() =
        runTest {
            val (deps, _) = depsWithField("Hello", loseSets = true)

            McpIntegrationTestHelper.withTestApplication(deps) { client, _ ->
                val result =
                    client.callTool(
                        name = "android_type_clear_text",
                        arguments = mapOf("node_id" to "node_edit"),
                    )
                assertEquals(true, result.isError)
                val text = (result.content[0] as TextContent).text
                assertTrue(text.contains("was not deleted"), text)
            }
        }

    @Test
    fun `type_clear_text with a selector clears the matched field`() =
        runTest {
            val (deps, fieldText) = depsWithField("Hello")
            every {
                deps.elementFinder.findElements(
                    any<List<WindowData>>(),
                    FindBy.CLASS_NAME,
                    "android.widget.EditText",
                    true,
                )
            } returns
                listOf(
                    ElementInfo(
                        id = "node_edit",
                        className = "android.widget.EditText",
                        bounds = BoundsData(50, 800, 500, 900),
                        editable = true,
                        enabled = true,
                        visible = true,
                    ),
                )

            McpIntegrationTestHelper.withTestApplication(deps) { client, _ ->
                val result =
                    client.callTool(
                        name = "android_type_clear_text",
                        arguments =
                            mapOf("by" to "class_name", "value" to "android.widget.EditText", "exact_match" to true),
                    )
                assertNotEquals(true, result.isError)
                assertTrue((result.content[0] as TextContent).text.contains("Text cleared from node 'node_edit'"))
            }
            assertEquals("", fieldText())
        }

    @Test
    fun `press_key still works after tool changes`() =
        runTest {
            val deps = McpIntegrationTestHelper.createMockDependencies()
            coEvery { deps.actionExecutor.pressBack() } returns Result.success(Unit)

            McpIntegrationTestHelper.withTestApplication(deps) { client, _ ->
                val result =
                    client.callTool(
                        name = "android_press_key",
                        arguments = mapOf("key" to "BACK"),
                    )
                assertNotEquals(true, result.isError)
                val text = (result.content[0] as TextContent).text
                assertTrue(text.contains("BACK"))
            }
        }

    @Test
    fun `listTools verifies correct tool set`() =
        runTest {
            McpIntegrationTestHelper.withTestApplication { client, _ ->
                val result = client.listTools()
                val toolNames = result.tools.map { it.name }.toSet()

                // New tools must be present
                assertTrue(toolNames.contains("android_type_append_text"))
                assertTrue(toolNames.contains("android_type_insert_text"))
                assertTrue(toolNames.contains("android_type_replace_text"))
                assertTrue(toolNames.contains("android_type_clear_text"))
                assertTrue(toolNames.contains("android_press_key"))

                // Old tools must NOT be present
                assertFalse(toolNames.contains("android_input_text"))
                assertFalse(toolNames.contains("android_clear_text"))
                assertFalse(toolNames.contains("android_set_text"))
            }
        }
}

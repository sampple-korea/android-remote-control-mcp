package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.integration.McpIntegrationTestHelper
import com.danielealbano.androidremotecontrolmcp.mcp.InFlightToolCalls
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.testutil.RecordingServerLogRepository
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("batch_actions")
class BatchActionsToolsTest {
    private val server =
        Server(
            Implementation(name = "test", version = "test"),
            ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
        )
    private val registrar =
        LoggedToolRegistrar(server, RecordingServerLogRepository(), ToolCallIndicator.NONE, InFlightToolCalls())
    private val handler = BatchActionsHandler(registrar)
    private val calls = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        McpIntegrationTestHelper.mockAndroidLog()
        handler.register(registrar, PREFIX)
        fakeTool("tap") { McpToolUtils.textResult("tapped") }
        fakeTool("press_back") { McpToolUtils.textResult("back pressed") }
        fakeTool("click_node") { throw McpToolException.ActionFailed("node not found") }
        fakeTool("wait_for_idle") { CallToolResult(content = listOf(TextContent("idle timeout")), isError = true) }
    }

    @AfterEach
    fun tearDown() {
        McpIntegrationTestHelper.unmockAndroidLog()
    }

    /** Registers a fake step tool under its un-prefixed name, recording every invocation in [calls]. */
    private fun fakeTool(
        name: String,
        behavior: suspend () -> CallToolResult,
    ) {
        registrar.addTool(
            toolName = name,
            name = "$PREFIX$name",
            description = "fake $name",
            inputSchema = ToolSchema(),
        ) {
            calls += name
            behavior()
        }
    }

    private fun batchArgs(
        vararg steps: Pair<String, JsonObject?>,
        continueOnError: Boolean? = null,
        returnScreenState: Boolean? = null,
    ): JsonObject =
        buildJsonObject {
            putJsonArray("actions") {
                for ((tool, stepArgs) in steps) {
                    addJsonObject {
                        put("tool", tool)
                        if (stepArgs != null) put("arguments", stepArgs)
                    }
                }
            }
            continueOnError?.let { put("continue_on_error", it) }
            returnScreenState?.let { put("return_screen_state", it) }
        }

    private fun resultText(result: CallToolResult): String = (result.content.single() as TextContent).text

    @Test
    fun `registers itself with the device prefix`() {
        assertTrue(server.tools.keys.contains("${PREFIX}batch_actions"), "${server.tools.keys}")
    }

    @Test
    fun `runs allowed steps in order and aggregates their results`() =
        runTest {
            val result = handler.execute(batchArgs("tap" to null, "press_back" to buildJsonObject {}))

            assertEquals(listOf("tap", "press_back"), calls)
            assertNull(result.isError)
            val text = resultText(result)
            assertTrue(text.contains("batch: 2/2 steps succeeded"), text)
            assertTrue(text.contains("--- step 1/2: tap — OK"), text)
            assertTrue(text.contains("tapped"), text)
            assertTrue(text.contains("--- step 2/2: press_back — OK"), text)
            assertTrue(text.contains("back pressed"), text)
        }

    @Test
    fun `accepts step tool names carrying the device prefix`() =
        runTest {
            val result = handler.execute(batchArgs("${PREFIX}tap" to null))

            assertEquals(listOf("tap"), calls)
            assertNull(result.isError)
            assertTrue(resultText(result).contains("--- step 1/1: tap — OK"), resultText(result))
        }

    @Test
    fun `stops at the first failing step by default`() =
        runTest {
            val result =
                handler.execute(batchArgs("tap" to null, "click_node" to null, "press_back" to null))

            assertEquals(listOf("tap", "click_node"), calls)
            assertEquals(true, result.isError)
            val text = resultText(result)
            assertTrue(text.contains("batch: 1/3 steps succeeded (stopped at step 2)"), text)
            assertTrue(text.contains("--- step 2/3: click_node — FAILED"), text)
            assertTrue(text.contains("node not found"), text)
            assertFalse(text.contains("step 3/3"), text)
        }

    @Test
    fun `continue_on_error runs the remaining steps after a failure`() =
        runTest {
            val result =
                handler.execute(
                    batchArgs("tap" to null, "click_node" to null, "press_back" to null, continueOnError = true),
                )

            assertEquals(listOf("tap", "click_node", "press_back"), calls)
            assertEquals(true, result.isError)
            val text = resultText(result)
            assertTrue(text.contains("batch: 2/3 steps succeeded"), text)
            assertFalse(text.contains("stopped at step"), text)
            assertTrue(text.contains("--- step 3/3: press_back — OK"), text)
        }

    @Test
    fun `a step returning an error result counts as a failure`() =
        runTest {
            val result = handler.execute(batchArgs("wait_for_idle" to null, "tap" to null))

            assertEquals(listOf("wait_for_idle"), calls)
            assertEquals(true, result.isError)
            val text = resultText(result)
            assertTrue(text.contains("batch: 0/2 steps succeeded (stopped at step 1)"), text)
            assertTrue(text.contains("--- step 1/2: wait_for_idle — FAILED"), text)
            assertTrue(text.contains("idle timeout"), text)
        }

    @Test
    fun `a whitelisted but unregistered tool fails its step`() =
        runTest {
            // scroll is in the allowlist but no fake registered it (as when the tool is
            // disabled in the tool permissions).
            val result = handler.execute(batchArgs("scroll" to null, "tap" to null))

            assertEquals(emptyList<String>(), calls)
            assertEquals(true, result.isError)
            val text = resultText(result)
            assertTrue(text.contains("--- step 1/2: scroll — FAILED"), text)
            assertTrue(text.contains("not available"), text)
        }

    /** Asserts [handler] rejects [arguments] with [McpToolException.InvalidParams] before running anything. */
    private fun assertInvalid(arguments: JsonObject?): McpToolException.InvalidParams =
        assertThrows<McpToolException.InvalidParams> { runBlocking { handler.execute(arguments) } }

    @Test
    fun `rejects a tool outside the allowlist before running anything`() {
        val exception = assertInvalid(batchArgs("tap" to null, "delete_file" to null))

        assertTrue(exception.message!!.contains("delete_file"), "${exception.message}")
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `rejects nesting batch_actions`() {
        assertInvalid(batchArgs("batch_actions" to null))
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `rejects missing, empty, and oversized actions`() {
        assertInvalid(null)
        assertInvalid(buildJsonObject {})
        assertInvalid(batchArgs())
        assertInvalid(batchArgs(*Array(BatchActionsHandler.MAX_ACTIONS + 1) { "tap" to null }))
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `rejects malformed step entries`() {
        val notAnObject =
            buildJsonObject {
                putJsonArray("actions") { add(JsonPrimitive(42)) }
            }
        assertInvalid(notAnObject)

        val missingTool =
            buildJsonObject {
                putJsonArray("actions") { addJsonObject { put("arguments", buildJsonObject {}) } }
            }
        assertInvalid(missingTool)

        val argumentsNotAnObject =
            buildJsonObject {
                putJsonArray("actions") {
                    addJsonObject {
                        put("tool", "tap")
                        put("arguments", "x=1")
                    }
                }
            }
        assertInvalid(argumentsNotAnObject)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `return_screen_state appends one fresh capture with a single warning`() =
        runTest {
            fakeTool("get_screen_state") { McpToolUtils.untrustedTextResult("screen:1080x2400") }

            val result = handler.execute(batchArgs("tap" to null, returnScreenState = true))

            assertEquals(listOf("tap", "get_screen_state"), calls)
            assertNull(result.isError)
            val text = resultText(result)
            assertTrue(text.contains("--- screen state (fresh get_screen_state capture)"), text)
            assertTrue(text.contains("screen:1080x2400"), text)
            assertEquals(1, text.split(McpToolUtils.UNTRUSTED_CONTENT_WARNING).size - 1, text)
        }

    @Test
    fun `return_screen_state degrades to a note when the tool is unavailable`() =
        runTest {
            val result = handler.execute(batchArgs("tap" to null, returnScreenState = true))

            assertNull(result.isError)
            assertTrue(resultText(result).contains("no screen state returned"), resultText(result))
        }

    private companion object {
        const val PREFIX = "android_"
    }
}

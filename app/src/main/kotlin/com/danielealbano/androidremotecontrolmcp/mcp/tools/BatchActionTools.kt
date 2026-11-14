@file:Suppress("MatchingDeclarationName")

package com.danielealbano.androidremotecontrolmcp.mcp.tools

import android.util.Log
import com.danielealbano.androidremotecontrolmcp.data.model.ToolPermissionsConfig
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// ─────────────────────────────────────────────────────────────────────────────
// batch_actions
// ─────────────────────────────────────────────────────────────────────────────

/**
 * MCP tool handler for `batch_actions`.
 *
 * Runs a list of action steps in order inside a single tool call, dispatching each step to the
 * target tool's own handler (looked up in the [LoggedToolRegistrar] registry), and returns every
 * step's outcome in one combined result. Only manipulation tools run in a batch — see
 * [ALLOWED_TOOLS]; read-only tools, file/shell/camera/intent/notification/location tools and
 * `batch_actions` itself are refused before anything runs.
 *
 * A step fails when its tool returns an error result or throws; the batch then stops at that
 * step unless `continue_on_error=true`. Steps never invoke `get_screen_state`, so the diff
 * baseline is untouched by a batch; `return_screen_state=true` appends one fresh capture at the
 * end, which — like a direct call — becomes the new baseline.
 */
class BatchActionsHandler(
    private val toolHandlers: LoggedToolRegistrar,
) {
    private var toolNamePrefix: String = ""

    suspend fun execute(arguments: JsonObject?): CallToolResult {
        val steps = parseSteps(arguments)
        validateAllowlist(steps)
        val continueOnError = McpToolUtils.optionalBoolean(arguments, PARAM_CONTINUE_ON_ERROR, false)
        val returnScreenState = McpToolUtils.optionalBoolean(arguments, PARAM_RETURN_SCREEN_STATE, false)

        val sections = mutableListOf<String>()
        var succeeded = 0
        var failures = 0
        var stoppedAtStep = -1
        for ((index, step) in steps.withIndex()) {
            val outcome = executeStep(step)
            sections += buildString {
                append("--- step ${index + 1}/${steps.size}: ${step.toolName} — ")
                append(if (outcome.ok) "OK" else "FAILED")
                if (outcome.text.isNotEmpty()) {
                    append('\n')
                    append(outcome.text)
                }
            }
            if (outcome.ok) {
                succeeded++
            } else {
                failures++
                if (!continueOnError) {
                    if (index + 1 < steps.size) stoppedAtStep = index + 1
                    break
                }
            }
        }
        if (returnScreenState) {
            sections += captureScreenStateSection()
        }

        val summary = buildString {
            append("batch: $succeeded/${steps.size} steps succeeded")
            if (stoppedAtStep > 0) append(" (stopped at step $stoppedAtStep)")
        }
        Log.d(TAG, "batch_actions: steps=${steps.size} succeeded=$succeeded failures=$failures")
        val body = (listOf(summary) + sections).joinToString("\n")
        return CallToolResult(
            content = listOf(TextContent(text = "${McpToolUtils.UNTRUSTED_CONTENT_WARNING}\n$body")),
            isError = if (failures > 0) true else null,
        )
    }

    /** A step outcome: [ok] is false for an error result or a thrown failure; [text] is the step's own output. */
    private data class StepOutcome(
        val ok: Boolean,
        val text: String,
    )

    @Suppress("TooGenericExceptionCaught")
    private suspend fun executeStep(step: BatchStep): StepOutcome {
        val handler =
            toolHandlers.handlerFor(step.toolName)
                ?: return StepOutcome(
                    false,
                    "tool '${step.toolName}' is not available " +
                        "(not registered, or disabled in the tool permissions)",
                )
        val result =
            try {
                handler(
                    CallToolRequest(
                        CallToolRequestParams(
                            name = "$toolNamePrefix${step.toolName}",
                            arguments = step.arguments,
                        ),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return StepOutcome(false, e.message ?: "unknown error")
            }
        return StepOutcome(result.isError != true, resultText(result))
    }

    /**
     * The trailing fresh `get_screen_state` capture for `return_screen_state=true`, rendered as one
     * section. A missing/disabled tool or a failed capture degrades to a note line — the batch
     * outcome never depends on it. The capture's own untrusted-content warning is stripped: the
     * batch result already carries it once at the top.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun captureScreenStateSection(): String {
        val header = "--- screen state (fresh ${GetScreenStateHandler.TOOL_NAME} capture)"
        val handler =
            toolHandlers.handlerFor(GetScreenStateHandler.TOOL_NAME)
                ?: return "$header\nnote: ${GetScreenStateHandler.TOOL_NAME} is not available; no screen state returned"
        val result =
            try {
                handler(
                    CallToolRequest(
                        CallToolRequestParams(
                            name = "$toolNamePrefix${GetScreenStateHandler.TOOL_NAME}",
                            arguments = buildJsonObject {},
                        ),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return "$header\nnote: the screen state capture failed: ${e.message ?: "unknown error"}"
            }
        if (result.isError == true) {
            return "$header\nnote: the screen state capture failed: ${resultText(result)}"
        }
        val text = resultText(result).removePrefix("${McpToolUtils.UNTRUSTED_CONTENT_WARNING}\n")
        return "$header\n$text"
    }

    /** The text of a step result; non-text blocks (a step never returns any today) are noted, not dropped silently. */
    private fun resultText(result: CallToolResult): String =
        result.content.joinToString("\n") { block ->
            when (block) {
                is TextContent -> block.text
                is ImageContent -> "[image content omitted]"
                else -> "[non-text content omitted]"
            }
        }

    private data class BatchStep(
        val toolName: String,
        val arguments: JsonObject?,
    )

    /** Parses and validates the `actions` array structure; allowlist checks are [validateAllowlist]. */
    private fun parseSteps(arguments: JsonObject?): List<BatchStep> {
        val element =
            arguments?.get(PARAM_ACTIONS)
                ?: throw McpToolException.InvalidParams("Missing required parameter: '$PARAM_ACTIONS'")
        val array =
            element as? JsonArray
                ?: throw McpToolException.InvalidParams("Parameter '$PARAM_ACTIONS' must be an array of action objects")
        if (array.isEmpty()) {
            throw McpToolException.InvalidParams("Parameter '$PARAM_ACTIONS' must not be empty")
        }
        if (array.size > MAX_ACTIONS) {
            throw McpToolException.InvalidParams(
                "Parameter '$PARAM_ACTIONS' holds at most $MAX_ACTIONS actions, got: ${array.size}",
            )
        }
        return array.mapIndexed { index, item -> parseStep(index, item) }
    }

    private fun parseStep(
        index: Int,
        element: JsonElement,
    ): BatchStep {
        val obj =
            element as? JsonObject
                ?: throw McpToolException.InvalidParams(
                    "actions[$index] must be an object with a 'tool' name and optional 'arguments'",
                )
        val toolElement =
            obj["tool"]
                ?: throw McpToolException.InvalidParams("actions[$index] is missing the 'tool' name")
        val toolPrimitive = toolElement as? JsonPrimitive
        if (toolPrimitive == null || !toolPrimitive.isString || toolPrimitive.content.isBlank()) {
            throw McpToolException.InvalidParams("actions[$index].tool must be a non-empty string")
        }
        val stepArguments =
            when (val argsElement = obj["arguments"]) {
                null, JsonNull -> null
                is JsonObject -> argsElement
                else -> throw McpToolException.InvalidParams("actions[$index].arguments must be an object")
            }
        return BatchStep(normalizeToolName(toolPrimitive.content), stepArguments)
    }

    /** Accepts a step tool name with or without this device's prefix; the registry is keyed un-prefixed. */
    private fun normalizeToolName(raw: String): String =
        if (toolNamePrefix.isNotEmpty() && raw.startsWith(toolNamePrefix)) {
            raw.removePrefix(toolNamePrefix)
        } else {
            raw
        }

    /**
     * Refuses the whole batch before anything runs when a step names a tool outside [ALLOWED_TOOLS]:
     * a sequence must never half-execute before discovering a forbidden step at the end.
     */
    private fun validateAllowlist(steps: List<BatchStep>) {
        for (step in steps) {
            if (step.toolName !in ALLOWED_TOOLS) {
                throw McpToolException.InvalidParams(
                    "Tool '${step.toolName}' cannot run in a batch (not in the allowlist). " +
                        "See the $TOOL_NAME tool description for the allowed tools.",
                )
            }
        }
    }

    fun register(
        registrar: LoggedToolRegistrar,
        toolNamePrefix: String,
    ) {
        this.toolNamePrefix = toolNamePrefix
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$toolNamePrefix$TOOL_NAME",
            description =
                "Runs several action tools in order in ONE call and returns every step's result " +
                    "together, so a whole interaction sequence (open an app, wait for a node, tap " +
                    "it, type text, press a key) costs a single round trip. Each action names a " +
                    "tool WITHOUT the device prefix (e.g. \"tap\"; the prefixed name is accepted " +
                    "and the prefix stripped) plus that tool's arguments object. Allowed tools: " +
                    ALLOWED_TOOLS.joinToString(", ") +
                    ". Read-only tools (get_screen_state, find_nodes, get_node_details, " +
                    "get_clipboard, the list_* tools), file, shell, camera, intent, notification " +
                    "and location tools cannot run in a batch, and batches cannot nest. Steps run " +
                    "in order and are NOT rolled back; the first failing step stops the batch " +
                    "unless continue_on_error=true. At most $MAX_ACTIONS actions per call. A batch " +
                    "does not return the screen state and never touches the get_screen_state diff " +
                    "baseline: call ${toolNamePrefix}get_screen_state afterwards as usual, or set " +
                    "return_screen_state=true to append one fresh capture at the end (which, like " +
                    "a direct call, becomes the new diff baseline).",
            inputSchema =
                ToolSchema(
                    properties = batchActionsProperties(),
                    required = listOf(PARAM_ACTIONS),
                ),
        ) { request -> execute(request.arguments) }
    }

    companion object {
        const val TOOL_NAME = "batch_actions"
        internal const val PARAM_ACTIONS = "actions"
        internal const val PARAM_CONTINUE_ON_ERROR = "continue_on_error"
        internal const val PARAM_RETURN_SCREEN_STATE = "return_screen_state"
        internal const val MAX_ACTIONS = 50
        private const val TAG = "MCP:BatchActions"

        /**
         * The manipulation tools a batch may run: touch, gestures, node actions, text input,
         * system keys, waits, clipboard set, and app launch. Deliberately NOT allowed:
         * `close_app` (kills the app's process), the intent tools (arbitrary activity
         * launches), and everything read-only or destructive in the file/shell/camera/
         * notification/location/sharing groups.
         */
        internal val ALLOWED_TOOLS: Set<String> =
            setOf(
                // Touch actions
                "tap",
                "long_press",
                "double_tap",
                "swipe",
                "scroll",
                // Gestures
                "pinch",
                "custom_gesture",
                // Node actions
                "click_node",
                "long_click_node",
                "perform_node_action",
                "tap_node",
                // Text input
                "edit_text",
                "press_key",
                // System actions
                "press_back",
                "press_home",
                "press_recents",
                "open_notifications",
                "open_quick_settings",
                "dismiss_keyboard",
                // Utilities (waits and clipboard set)
                "wait_for_node",
                "wait_for_idle",
                "set_clipboard",
                // App management (launch only; close_app kills the process and stays out)
                "open_app",
            )
    }
}

private fun batchActionsProperties(): JsonObject =
    buildJsonObject {
        putJsonObject(BatchActionsHandler.PARAM_ACTIONS) {
            put("type", "array")
            put(
                "description",
                "The actions to run, in order. Each item names one allowed tool and its arguments. " +
                    "At most ${BatchActionsHandler.MAX_ACTIONS} actions.",
            )
            putJsonObject("items") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("tool") {
                        put("type", "string")
                        put(
                            "description",
                            "Un-prefixed tool name, e.g. \"tap\" (the device prefix, e.g. \"android_\", " +
                                "may be included; it is stripped).",
                        )
                    }
                    putJsonObject("arguments") {
                        put("type", "object")
                        put(
                            "description",
                            "The tool's arguments, exactly as in a direct call. " +
                                "Omit for tools that take no parameters.",
                        )
                    }
                }
                putJsonArray("required") { add(JsonPrimitive("tool")) }
            }
        }
        putJsonObject(BatchActionsHandler.PARAM_CONTINUE_ON_ERROR) {
            put("type", "boolean")
            put(
                "description",
                "Keep running the remaining actions after a failing step. By default the batch " +
                    "stops at the first failure and reports the steps that ran.",
            )
            put("default", false)
        }
        putJsonObject(BatchActionsHandler.PARAM_RETURN_SCREEN_STATE) {
            put("type", "boolean")
            put(
                "description",
                "Append a fresh get_screen_state capture (no diff) after the last step. " +
                    "Off by default: prefer a separate get_screen_state call.",
            )
            put("default", false)
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// Registration function
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Registers the batch actions tool. Called LAST by the registration wiring: the handler looks its
 * step handlers up in the registrar at call time, and every other tool must already be registered
 * for the batch to find them (registration completes before the server starts serving).
 */
fun registerBatchActionTools(
    registrar: LoggedToolRegistrar,
    toolNamePrefix: String,
    perms: ToolPermissionsConfig,
) {
    if (perms.isToolEnabled(BatchActionsHandler.TOOL_NAME)) {
        BatchActionsHandler(registrar).register(registrar, toolNamePrefix)
    }
}

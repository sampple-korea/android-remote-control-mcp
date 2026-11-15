@file:Suppress("TooManyFunctions")

package com.danielealbano.androidremotecontrolmcp.mcp.tools

import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.danielealbano.androidremotecontrolmcp.data.model.ServerLogEntry
import com.danielealbano.androidremotecontrolmcp.data.model.ToolPermissionsConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.ServerLogRepository
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.privacy.PlaceholderSubstitutor
import com.danielealbano.androidremotecontrolmcp.privacy.PrivacyToolGate
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityNodeCache
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityNodeData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityServiceProvider
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityTreeLock
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityTreeParser
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ActionExecutor
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ElementFinder
import com.danielealbano.androidremotecontrolmcp.services.accessibility.WindowData
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

// Shared constants for type tools
private const val MAX_TEXT_LENGTH = 2000
private const val DEFAULT_TYPING_SPEED_MS = 250
private const val DEFAULT_TYPING_VARIANCE_MS = 50
private const val MIN_TYPING_SPEED_MS = 10
private const val MAX_TYPING_SPEED_MS = 5000
private const val MAX_SURROUNDING_TEXT_LENGTH = 10000

/**
 * Standard trailing sentences shared by every text-entry tool description: the field's new content is
 * computed from its current text and set in one operation, and the content is returned for verification.
 * Kept as one helper so the common tail stays DRY and does not lengthen each `register` function.
 */
private fun verificationAndSetTextHint(): String =
    "Returns the field content after the operation for verification. " +
        "The new content is computed from the field's current text and set in one operation " +
        "(accessibility ACTION_SET_TEXT): no key events are sent, the field is not clicked and the soft " +
        "keyboard is not shown, so the value cannot be mangled by keyboard autocomplete or glide typing."

/**
 * Mutex serializing all type tool operations, so two concurrent calls cannot read the same field content,
 * compute from it and overwrite each other's result.
 */
internal val typeOperationMutex = Mutex()

/**
 * Pure text computations behind the type tools. A tool reads the field's current text from the accessibility
 * tree, computes the new content with one of these functions and sets the result in one ACTION_SET_TEXT
 * operation (see [applySetText]), so no synthetic key event ever reaches the target app's input method.
 * All offsets are UTF-16 code units, matching [String] indexing.
 */
internal object TextEditCalculator {
    /** [current] with [text] appended at the end. */
    fun appended(
        current: String,
        text: String,
    ): String = current + text

    /**
     * [current] with [text] inserted at [offset].
     *
     * @throws IllegalArgumentException when [offset] is outside `0..current.length`; callers validate first
     *   so they can report the offset against the field they read.
     */
    fun inserted(
        current: String,
        offset: Int,
        text: String,
    ): String {
        require(offset in 0..current.length) {
            "offset $offset out of bounds for text of length ${current.length}"
        }
        return current.substring(0, offset) + text + current.substring(offset)
    }

    /** [current] with the first occurrence of [search] replaced by [newText], or null when [search] is absent. */
    fun replaced(
        current: String,
        search: String,
        newText: String,
    ): String? {
        val index = current.indexOf(search)
        return if (index < 0) null else current.replaceRange(index, index + search.length, newText)
    }
}

/**
 * Runs [block] as a text-edit tool call. [McpToolException]s — the tools' expected failures — pass through
 * unchanged and [CancellationException] is rethrown, but any other exception is recorded in the server log
 * and converted to [McpToolException.ActionFailed]: a defect or a framework failure inside a text tool must
 * surface as an ordinary tool error, never as an uncaught exception escaping the tool handler.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal suspend inline fun <T> guardedTextEdit(
    toolName: String,
    serverLogRepository: ServerLogRepository,
    block: suspend () -> T,
): T {
    try {
        return block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: McpToolException) {
        throw e
    } catch (e: Exception) {
        serverLogRepository.log(
            ServerLogEntry.Type.TOOL_CALL,
            "Unexpected failure in $toolName: ${e.javaClass.simpleName}: ${e.message}",
            toolName,
            null,
        )
        throw McpToolException.ActionFailed(
            "Unexpected error while running $toolName: ${e.message ?: e.javaClass.simpleName}",
        )
    }
}

/**
 * Validates and extracts typing speed parameters from tool arguments.
 * Uses [McpToolUtils.optionalInt] for consistent parameter parsing with
 * proper type checking (rejects string-encoded numbers, floats, etc.).
 *
 * The parameters are legacy: they paced the key-event typing these tools used before the text was set in one
 * operation. They are still parsed and validated so existing callers see the same errors, but the values no
 * longer affect anything.
 *
 * @return Pair of (typingSpeed, typingSpeedVariance) in ms.
 * @throws McpToolException.InvalidParams if values are out of range.
 */
@Suppress("ThrowsCount")
internal fun extractTypingParams(arguments: JsonObject?): Pair<Int, Int> {
    val typingSpeed = McpToolUtils.optionalInt(arguments, "typing_speed", DEFAULT_TYPING_SPEED_MS)

    if (typingSpeed < MIN_TYPING_SPEED_MS) {
        throw McpToolException.InvalidParams(
            "typing_speed must be >= $MIN_TYPING_SPEED_MS ms, got $typingSpeed",
        )
    }
    if (typingSpeed > MAX_TYPING_SPEED_MS) {
        throw McpToolException.InvalidParams(
            "typing_speed must be <= $MAX_TYPING_SPEED_MS ms, got $typingSpeed",
        )
    }

    val typingSpeedVariance = McpToolUtils.optionalInt(arguments, "typing_speed_variance", DEFAULT_TYPING_VARIANCE_MS)

    if (typingSpeedVariance < 0) {
        throw McpToolException.InvalidParams(
            "typing_speed_variance must be >= 0, got $typingSpeedVariance",
        )
    }

    return typingSpeed to typingSpeedVariance
}

/**
 * Validates that text length does not exceed the maximum.
 *
 * @throws McpToolException.InvalidParams if text exceeds MAX_TEXT_LENGTH.
 */
internal fun validateTextLength(
    text: String,
    paramName: String = "text",
) {
    if (text.length > MAX_TEXT_LENGTH) {
        throw McpToolException.InvalidParams(
            "$paramName exceeds maximum length of $MAX_TEXT_LENGTH characters (got ${text.length})",
        )
    }
}

/** [node] when it is editable, else the first editable node inside it (depth first), else null. */
private fun firstEditable(node: AccessibilityNodeData): AccessibilityNodeData? =
    if (node.editable) node else node.children.firstNotNullOfOrNull(::firstEditable)

private fun findInTree(
    node: AccessibilityNodeData,
    nodeId: String,
): AccessibilityNodeData? {
    if (node.id == nodeId) return node
    return node.children.firstNotNullOfOrNull { findInTree(it, nodeId) }
}

/** The node of [nodeId] in the trees of [windows], or null when it is not in the read. */
private fun findTargetNode(
    windows: List<WindowData>,
    nodeId: String,
): AccessibilityNodeData? = windows.firstNotNullOfOrNull { findInTree(it.tree, nodeId) }

/**
 * The editable field a type tool edits for target [nodeId]: the node itself when it is editable, else the
 * first editable node inside it (depth first) — a wrapper around the real field is a valid target.
 *
 * @throws McpToolException.NodeNotFound when [nodeId] is not in [windows].
 * @throws McpToolException.ActionFailed when neither the node nor anything inside it is editable.
 */
internal fun editableFieldOf(
    windows: List<WindowData>,
    nodeId: String,
): AccessibilityNodeData {
    val target =
        findTargetNode(windows, nodeId)
            ?: throw McpToolException.NodeNotFound("Node '$nodeId' not found in accessibility tree")
    return firstEditable(target)
        ?: throw McpToolException.ActionFailed(
            "Node '$nodeId' is not an editable text field and contains no editable field to set text on",
        )
}

/**
 * Sets [newText] on field [fieldNodeId] in one ACTION_SET_TEXT operation, mapping a failure the same way as
 * the other node actions. No click or focus change is involved, so the soft keyboard is never shown.
 */
internal suspend fun applySetText(
    actionExecutor: ActionExecutor,
    fieldNodeId: String,
    newText: String,
    windows: List<WindowData>,
) {
    actionExecutor.setTextOnNode(fieldNodeId, newText, windows).onFailure { e ->
        mapNodeActionException(e, fieldNodeId)
    }
}

/**
 * What a type tool read back from the field it edited, kept for the redaction that runs outside the typing
 * lock: the [content] (null when the field could not be read back), the [fieldNodeId] and the [windows] its
 * context comes from, and whether its editor is a password input.
 */
internal data class FieldRead(
    val content: String?,
    val fieldNodeId: String,
    val isPasswordInput: Boolean,
    val windows: List<WindowData>,
)

/**
 * Re-reads the tree after an edit and returns the [FieldRead] of field [fieldNodeId]: its text and password
 * flag from the fresh read, with content null when the field is no longer in the tree.
 */
internal fun readBackField(
    treeParser: AccessibilityTreeParser,
    accessibilityServiceProvider: AccessibilityServiceProvider,
    nodeCache: AccessibilityNodeCache,
    fieldNodeId: String,
): FieldRead {
    val windows = getFreshRead(treeParser, accessibilityServiceProvider, nodeCache).result.windows
    val node = findTargetNode(windows, fieldNodeId)
    return FieldRead(node?.text, fieldNodeId, node?.isPassword == true, windows)
}

/**
 * The "Field content" of a type tool response: [read]'s content redacted with the field's own context, as a
 * password when its editor is a password input, or [FIELD_CONTENT_UNREADABLE] when the field could not be
 * read (a server string, never redacted).
 */
internal suspend fun responseFieldContent(
    privacyToolGate: PrivacyToolGate,
    read: FieldRead,
): String =
    read.content?.let { privacyToolGate.nodeText(read.windows, it, read.fieldNodeId, read.isPasswordInput) }
        ?: FIELD_CONTENT_UNREADABLE

internal const val FIELD_CONTENT_UNREADABLE = "(unable to read field content)"

/**
 * MCP tool: edit_text
 *
 * The one text-field editing tool, with a `mode` parameter (it replaces the former
 * type_append_text, type_insert_text, type_replace_text and type_clear_text tools):
 * - `append`: append [text] at the end of the field.
 * - `insert`: insert [text] at character offset [offset].
 * - `replace`: replace the first occurrence of [search] with [new_text] (empty deletes it).
 * - `set`: set the whole field content to [text].
 * - `clear`: empty the field, verifying afterwards that the text is actually gone.
 *
 * Every mode reads the field's current text from a fresh accessibility read, computes the new
 * content ([TextEditCalculator]) and sets it in one ACTION_SET_TEXT operation. The field is never
 * clicked and no key event is sent, so the soft keyboard is not shown and the target app's input
 * method cannot alter the value.
 */
class EditTextTool
    @Inject
    constructor(
        private val treeParser: AccessibilityTreeParser,
        private val actionExecutor: ActionExecutor,
        private val accessibilityServiceProvider: AccessibilityServiceProvider,
        private val nodeCache: AccessibilityNodeCache,
        private val privacyToolGate: PrivacyToolGate,
        private val substitutor: PlaceholderSubstitutor,
        private val targetResolver: NodeTargetResolver,
        private val serverLogRepository: ServerLogRepository,
    ) {
        suspend fun execute(arguments: JsonObject?): CallToolResult =
            guardedTextEdit(TOOL_NAME, serverLogRepository) {
                val mode = McpToolUtils.requireString(arguments, PARAM_MODE).lowercase()
                if (mode !in MODES) {
                    throw McpToolException.InvalidParams(
                        "Parameter '$PARAM_MODE' must be one of: ${MODES.joinToString(", ")}, got: '$mode'",
                    )
                }
                val target = targetResolver.parse(arguments)
                if (mode == MODE_CLEAR) {
                    executeClear(target)
                } else {
                    executeEdit(mode, arguments, target)
                }
            }

        /** The append / insert / replace / set modes: parse and validate first, then edit under the typing lock. */
        @Suppress("ThrowsCount", "LongMethod")
        private suspend fun executeEdit(
            mode: String,
            arguments: JsonObject?,
            target: NodeTarget,
        ): CallToolResult {
            var text = ""
            var offset = 0
            var search = ""
            var replacement = ""
            when (mode) {
                MODE_APPEND -> {
                    text = substitutor.substitute(McpToolUtils.requireString(arguments, "text"))
                    if (text.isEmpty()) {
                        throw McpToolException.InvalidParams("Parameter 'text' must be non-empty")
                    }
                    validateTextLength(text)
                }

                MODE_SET -> {
                    text = substitutor.substitute(McpToolUtils.requireString(arguments, "text"))
                    validateTextLength(text)
                }

                MODE_INSERT -> {
                    text = substitutor.substitute(McpToolUtils.requireString(arguments, "text"))
                    if (text.isEmpty()) {
                        throw McpToolException.InvalidParams("Parameter 'text' must be non-empty")
                    }
                    validateTextLength(text)
                    offset = McpToolUtils.requireInt(arguments, "offset")
                    if (offset < 0) {
                        throw McpToolException.InvalidParams("Parameter 'offset' must be >= 0, got $offset")
                    }
                }

                MODE_REPLACE -> {
                    // Substitute both the search term (to match the real field content) and the replacement.
                    search = substitutor.substitute(McpToolUtils.requireString(arguments, "search"))
                    if (search.isEmpty()) {
                        throw McpToolException.InvalidParams("Parameter 'search' must be non-empty")
                    }
                    if (search.length > MAX_SURROUNDING_TEXT_LENGTH) {
                        throw McpToolException.InvalidParams(
                            "search exceeds maximum length of $MAX_SURROUNDING_TEXT_LENGTH characters " +
                                "(got ${search.length})",
                        )
                    }
                    replacement = substitutor.substitute(McpToolUtils.requireString(arguments, "new_text"))
                    if (replacement.isNotEmpty()) {
                        validateTextLength(replacement, "new_text")
                    }
                }
            }

            // Legacy pacing parameters: validated as before, but the text is set in one operation now.
            extractTypingParams(arguments)

            val (nodeId, field, message) =
                typeOperationMutex.withLock {
                    val windows =
                        getFreshRead(treeParser, accessibilityServiceProvider, nodeCache).result.windows
                    val nodeId =
                        when (val resolution = targetResolver.resolve(target, windows)) {
                            is NodeResolution.Resolved -> resolution.nodeId
                            is NodeResolution.Ambiguous -> return resolution.result
                        }
                    val fieldNode = editableFieldOf(windows, nodeId)
                    val current = fieldNode.text ?: ""
                    val (newText, message) =
                        when (mode) {
                            MODE_APPEND -> {
                                TextEditCalculator.appended(current, text) to
                                    "Appended ${text.length} characters at end of node '$nodeId'."
                            }

                            MODE_SET -> {
                                text to
                                    "Set text of node '$nodeId' (${text.length} characters)."
                            }

                            MODE_INSERT -> {
                                if (offset > current.length) {
                                    throw McpToolException.InvalidParams(
                                        "offset ($offset) exceeds text length (${current.length}) in node '$nodeId'",
                                    )
                                }
                                TextEditCalculator.inserted(current, offset, text) to
                                    "Inserted ${text.length} characters at offset $offset in node '$nodeId'."
                            }

                            MODE_REPLACE -> {
                                val replaced =
                                    TextEditCalculator.replaced(current, search, replacement)
                                        ?: throw McpToolException.NodeNotFound(
                                            "Search text (${search.length} chars) not found in node '$nodeId'",
                                        )
                                replaced to
                                    "Replaced ${search.length} characters with ${replacement.length} " +
                                    "characters in node '$nodeId'."
                            }

                            else -> {
                                throw IllegalStateException("Unexpected edit mode: $mode")
                            }
                        }
                    applySetText(actionExecutor, fieldNode.id, newText, windows)
                    Triple(
                        nodeId,
                        readBackField(treeParser, accessibilityServiceProvider, nodeCache, fieldNode.id),
                        message,
                    )
                }

            Log.d(TAG, "edit_text: mode=$mode on node '$nodeId'")
            val redactedFieldContent = responseFieldContent(privacyToolGate, field)
            return McpToolUtils.untrustedTextResult(
                "$message\nField content: $redactedFieldContent",
            )
        }

        /** What happened under the typing lock in clear mode. */
        private sealed interface ClearOutcome {
            data class Ambiguous(
                val result: CallToolResult,
            ) : ClearOutcome

            data class AlreadyEmpty(
                val nodeId: String,
            ) : ClearOutcome

            data class Cleared(
                val nodeId: String,
                val field: FieldRead,
            ) : ClearOutcome
        }

        /** Clear mode: empty the field in one operation, then check that the text is gone. */
        private suspend fun executeClear(target: NodeTarget): CallToolResult =
            when (val outcome = typeOperationMutex.withLock { clearLocked(target) }) {
                is ClearOutcome.Ambiguous -> {
                    outcome.result
                }

                is ClearOutcome.AlreadyEmpty -> {
                    McpToolUtils.untrustedTextResult(
                        "Text cleared from node '${outcome.nodeId}'.\nField content: ",
                    )
                }

                is ClearOutcome.Cleared -> {
                    Log.d(TAG, "edit_text: cleared text on node '${outcome.nodeId}'")
                    val redactedFieldContent = responseFieldContent(privacyToolGate, outcome.field)
                    McpToolUtils.untrustedTextResult(
                        "Text cleared from node '${outcome.nodeId}'.\nField content: $redactedFieldContent",
                    )
                }
            }

        /** Resolves the target in a fresh read and clears its field; runs under [typeOperationMutex]. */
        @Suppress("ReturnCount")
        private suspend fun clearLocked(target: NodeTarget): ClearOutcome {
            val windows = getFreshRead(treeParser, accessibilityServiceProvider, nodeCache).result.windows
            val nodeId =
                when (val resolution = targetResolver.resolve(target, windows)) {
                    is NodeResolution.Resolved -> resolution.nodeId
                    is NodeResolution.Ambiguous -> return ClearOutcome.Ambiguous(resolution.result)
                }
            val fieldNode = editableFieldOf(windows, nodeId)
            val previousText = fieldNode.text ?: ""
            if (previousText.isEmpty()) {
                Log.d(TAG, "edit_text: field already empty on '$nodeId'")
                return ClearOutcome.AlreadyEmpty(nodeId)
            }

            applySetText(actionExecutor, fieldNode.id, "", windows)

            // Read the field back: a set the app silently dropped leaves the text unchanged, which must not
            // be reported as cleared. Any change counts — a field that fills itself again right after the
            // clear (e.g. restores a default) was still cleared, and its new content is what is reported.
            val readBack = readBackField(treeParser, accessibilityServiceProvider, nodeCache, fieldNode.id)
            if (readBack.content == previousText) {
                throw McpToolException.ActionFailed(
                    "The text in node '$nodeId' was not deleted: the field reads back unchanged",
                )
            }
            return ClearOutcome.Cleared(nodeId, readBack)
        }

        @Suppress("LongMethod")
        fun register(
            registrar: LoggedToolRegistrar,
            toolNamePrefix: String,
        ) {
            registrar.addTool(
                toolName = TOOL_NAME,
                name = "$toolNamePrefix$TOOL_NAME",
                description =
                    "Edit a text field, chosen by mode: 'append' adds text at the end, 'insert' adds " +
                        "text at a character offset, 'replace' swaps the first occurrence of 'search' " +
                        "for 'new_text', 'set' replaces the whole content with 'text', and 'clear' " +
                        "empties the field (verifying the text is gone). " +
                        "Maximum text length: $MAX_TEXT_LENGTH characters; for longer text use mode " +
                        "'append' repeatedly — each call appends to the field's current end. " +
                        verificationAndSetTextHint(),
                inputSchema =
                    ToolSchema(
                        properties =
                            buildJsonObject {
                                putNodeTargetProperties(toolNamePrefix)
                                putJsonObject(PARAM_MODE) {
                                    put("type", "string")
                                    put(
                                        "description",
                                        "Edit mode: append | insert | replace | set | clear. " +
                                            "append/insert/set use 'text'; insert also uses 'offset'; " +
                                            "replace uses 'search' and 'new_text'; clear uses neither.",
                                    )
                                }
                                putJsonObject("text") {
                                    put("type", "string")
                                    put(
                                        "description",
                                        "Text for modes append (must be non-empty), insert (must be " +
                                            "non-empty) and set; max $MAX_TEXT_LENGTH characters.",
                                    )
                                }
                                putJsonObject("offset") {
                                    put("type", "integer")
                                    put(
                                        "description",
                                        "Mode insert only: 0-based character offset (UTF-16 code " +
                                            "units) for the insertion position, within [0, current " +
                                            "text length]. Emoji and other supplementary characters " +
                                            "count as 2 units.",
                                    )
                                }
                                putJsonObject("search") {
                                    put("type", "string")
                                    put(
                                        "description",
                                        "Mode replace only: text to find in the field (first " +
                                            "occurrence, max $MAX_SURROUNDING_TEXT_LENGTH chars).",
                                    )
                                }
                                putJsonObject("new_text") {
                                    put("type", "string")
                                    put(
                                        "description",
                                        "Mode replace only: replacement text (max $MAX_TEXT_LENGTH " +
                                            "characters). Can be empty to just delete the found text.",
                                    )
                                }
                                putJsonObject("typing_speed") {
                                    put("type", "integer")
                                    put(
                                        "description",
                                        "Deprecated and ignored: the text is set in one operation, not typed. " +
                                            "Still validated (default: $DEFAULT_TYPING_SPEED_MS, " +
                                            "min: $MIN_TYPING_SPEED_MS, max: $MAX_TYPING_SPEED_MS)",
                                    )
                                }
                                putJsonObject("typing_speed_variance") {
                                    put("type", "integer")
                                    put(
                                        "description",
                                        "Deprecated and ignored: the text is set in one operation, not typed. " +
                                            "Still validated (>= 0, default: $DEFAULT_TYPING_VARIANCE_MS)",
                                    )
                                }
                            },
                        required = listOf(PARAM_MODE),
                    ),
            ) { request -> execute(request.arguments) }
        }

        companion object {
            private const val TAG = "MCP:EditTextTool"
            const val TOOL_NAME = "edit_text"
            internal const val PARAM_MODE = "mode"
            internal const val MODE_APPEND = "append"
            internal const val MODE_INSERT = "insert"
            internal const val MODE_REPLACE = "replace"
            internal const val MODE_SET = "set"
            internal const val MODE_CLEAR = "clear"
            internal val MODES = listOf(MODE_APPEND, MODE_INSERT, MODE_REPLACE, MODE_SET, MODE_CLEAR)
        }
    }

/**
 * MCP tool: press_key
 *
 * Presses a specific key. Supported keys: ENTER, DEL, TAB, SPACE.
 * (BACK and HOME are not here: use the dedicated press_back / press_home tools.)
 *
 * Key mapping strategy:
 * - ENTER: Use ACTION_IME_ENTER.
 * - DEL: Get current text from focused node, remove last character, set text.
 * - TAB, SPACE: Get current text from focused node, append character, set text.
 */
class PressKeyTool
    @Inject
    constructor(
        private val accessibilityServiceProvider: AccessibilityServiceProvider,
    ) {
        @Suppress("ThrowsCount")
        suspend fun execute(arguments: JsonObject?): CallToolResult {
            val key =
                arguments?.get("key")?.jsonPrimitive?.contentOrNull
                    ?: throw McpToolException.InvalidParams("Missing required parameter 'key'")

            val upperKey = key.uppercase()
            if (upperKey !in ALLOWED_KEYS) {
                throw McpToolException.InvalidParams(
                    "Invalid key: '$key'. Allowed values: ${ALLOWED_KEYS.joinToString(", ")}",
                )
            }

            when (upperKey) {
                "ENTER" -> {
                    pressEnter()
                }

                "DEL" -> {
                    pressDelete()
                }

                "TAB" -> {
                    appendCharToFocused('\t')
                }

                "SPACE" -> {
                    appendCharToFocused(' ')
                }
            }

            Log.d(TAG, "press_key: key=$upperKey succeeded")
            return McpToolUtils.textResult("Key '$upperKey' pressed successfully")
        }

        private fun pressEnter() {
            val focusedNode =
                findFocusedEditableNode(accessibilityServiceProvider)
                    ?: throw McpToolException.NodeNotFound(
                        "No focused node found for ENTER key",
                    )

            try {
                val success =
                    focusedNode.performAction(
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id,
                    )
                if (!success) {
                    throw McpToolException.ActionFailed("ENTER key action failed")
                }
            } finally {
                @Suppress("DEPRECATION")
                focusedNode.recycle()
            }
        }

        private fun pressDelete() {
            val focusedNode =
                findFocusedEditableNode(accessibilityServiceProvider)
                    ?: throw McpToolException.NodeNotFound(
                        "No focused editable node found for DEL key",
                    )

            try {
                val currentText = focusedNode.text?.toString() ?: ""
                if (currentText.isNotEmpty()) {
                    val newText = currentText.dropLast(1)
                    val arguments =
                        Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                newText,
                            )
                        }
                    val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    if (!success) {
                        throw McpToolException.ActionFailed("DEL key action failed")
                    }
                }
                // If text is already empty, DEL is a no-op (not an error)
            } finally {
                @Suppress("DEPRECATION")
                focusedNode.recycle()
            }
        }

        private fun appendCharToFocused(char: Char) {
            val focusedNode =
                findFocusedEditableNode(accessibilityServiceProvider)
                    ?: throw McpToolException.NodeNotFound(
                        "No focused editable node found for key input",
                    )

            try {
                val currentText = focusedNode.text?.toString() ?: ""
                val arguments =
                    Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            currentText + char,
                        )
                    }
                val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                if (!success) {
                    throw McpToolException.ActionFailed("Key input action failed")
                }
            } finally {
                @Suppress("DEPRECATION")
                focusedNode.recycle()
            }
        }

        fun register(
            registrar: LoggedToolRegistrar,
            toolNamePrefix: String,
        ) {
            registrar.addTool(
                toolName = TOOL_NAME,
                name = "$toolNamePrefix$TOOL_NAME",
                description =
                    "Press a specific key (ENTER, DEL, TAB, SPACE). " +
                        "For the back and home buttons use the press_back / press_home tools.",
                inputSchema =
                    ToolSchema(
                        properties =
                            buildJsonObject {
                                putJsonObject("key") {
                                    put("type", "string")
                                    put(
                                        "enum",
                                        buildJsonArray {
                                            add(JsonPrimitive("ENTER"))
                                            add(JsonPrimitive("DEL"))
                                            add(JsonPrimitive("TAB"))
                                            add(JsonPrimitive("SPACE"))
                                        },
                                    )
                                    put("description", "Key to press")
                                }
                            },
                        required = listOf("key"),
                    ),
            ) { request -> execute(request.arguments) }
        }

        companion object {
            private const val TAG = "MCP:PressKeyTool"
            const val TOOL_NAME = "press_key"
            private val ALLOWED_KEYS = setOf("ENTER", "DEL", "TAB", "SPACE")
        }
    }

/**
 * Registers all text input tools with the given [Server].
 */
@Suppress("LongParameterList")
fun registerTextInputTools(
    registrar: LoggedToolRegistrar,
    treeParser: AccessibilityTreeParser,
    elementFinder: ElementFinder,
    actionExecutor: ActionExecutor,
    accessibilityServiceProvider: AccessibilityServiceProvider,
    nodeCache: AccessibilityNodeCache,
    privacyToolGate: PrivacyToolGate,
    substitutor: PlaceholderSubstitutor,
    serverLogRepository: ServerLogRepository,
    toolNamePrefix: String,
    perms: ToolPermissionsConfig,
) {
    val targetResolver = NodeTargetResolver(elementFinder, privacyToolGate, substitutor)
    if (perms.isToolEnabled(EditTextTool.TOOL_NAME)) {
        EditTextTool(
            treeParser,
            actionExecutor,
            accessibilityServiceProvider,
            nodeCache,
            privacyToolGate,
            substitutor,
            targetResolver,
            serverLogRepository,
        ).register(registrar, toolNamePrefix)
    }
    if (perms.isToolEnabled(PressKeyTool.TOOL_NAME)) {
        PressKeyTool(accessibilityServiceProvider).register(registrar, toolNamePrefix)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared Utility for Text Input Tools
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Finds the currently input-focused editable node using the accessibility service.
 *
 * Uses [AccessibilityService.findFocus] with [AccessibilityNodeInfo.FOCUS_INPUT]
 * to locate the node that currently has keyboard focus.
 *
 * @return The focused [AccessibilityNodeInfo], or null if no editable node is focused.
 *         The caller is responsible for recycling the returned node.
 */
@Suppress("MaxLineLength")
internal fun findFocusedEditableNode(accessibilityServiceProvider: AccessibilityServiceProvider): AccessibilityNodeInfo? =
    // Serialize the clear+read critical section against concurrent MCP requests (see
    // AccessibilityTreeLock): a parallel request's cache clear must not wipe the framework tree
    // mid-traversal of this focused-node lookup.
    synchronized(AccessibilityTreeLock.monitor) {
        findFocusedEditableNodeLocked(accessibilityServiceProvider)
    }

@Suppress(
    "ReturnCount",
    "NestedBlockDepth",
    "CyclomaticComplexMethod",
    "LoopWithTooManyJumpStatements",
    "MaxLineLength",
)
private fun findFocusedEditableNodeLocked(accessibilityServiceProvider: AccessibilityServiceProvider): AccessibilityNodeInfo? {
    if (!accessibilityServiceProvider.isReady()) {
        throw McpToolException.PermissionDenied(
            "Accessibility service is not enabled",
        )
    }

    // Drop the framework's accessibility node cache before this live read. Chromium WebView
    // suppresses/throttles the content-changed events that invalidate that cache, so the focused
    // node's text could be stale — and DEL/TAB/SPACE (which read focusedNode.text to build the
    // ACTION_SET_TEXT payload) would then edit off a stale value, corrupting a WebView input whose
    // value changed via JavaScript. See AccessibilityServiceProvider.clearFrameworkNodeCache.
    accessibilityServiceProvider.clearFrameworkNodeCache()

    // Search across all windows for the focused editable node.
    // Uses a found-node variable to avoid returning from inside the try/finally block,
    // ensuring all AccessibilityWindowInfo objects are properly recycled before the
    // caller receives the result.
    val windows = accessibilityServiceProvider.getAccessibilityWindows()
    var foundNode: AccessibilityNodeInfo? = null
    try {
        for (window in windows) {
            val rootNode = window.root ?: continue
            val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

            if (focusedNode != null && focusedNode.isEditable) {
                // Recycle root AFTER confirming focusedNode is valid and editable.
                // On API 34+ recycle() is a no-op, but ordering is correct for safety.
                @Suppress("DEPRECATION")
                rootNode.recycle()
                foundNode = focusedNode
                break
            }

            // Not the node we want — recycle both
            @Suppress("DEPRECATION")
            rootNode.recycle()
            if (focusedNode != null) {
                @Suppress("DEPRECATION")
                focusedNode.recycle()
            }
        }
    } finally {
        // Recycle all AccessibilityWindowInfo objects for consistency
        // (no-op on API 34+, but follows the codebase's recycling convention)
        for (w in windows) {
            @Suppress("DEPRECATION")
            w.recycle()
        }
    }

    if (foundNode != null) return foundNode

    // Fallback: try rootInActiveWindow if getWindows() returned empty
    if (windows.isEmpty()) {
        val rootNode = accessibilityServiceProvider.getRootNode() ?: return null
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        @Suppress("DEPRECATION")
        rootNode.recycle()

        if (focusedNode != null && !focusedNode.isEditable) {
            @Suppress("DEPRECATION")
            focusedNode.recycle()
            return null
        }
        return focusedNode
    }

    return null
}

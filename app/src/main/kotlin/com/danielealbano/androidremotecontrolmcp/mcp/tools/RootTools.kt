package com.danielealbano.androidremotecontrolmcp.mcp.tools

import android.content.Context
import com.danielealbano.androidremotecontrolmcp.data.model.ServerLogEntry
import com.danielealbano.androidremotecontrolmcp.data.model.ShellLimit
import com.danielealbano.androidremotecontrolmcp.data.model.ToolPermissionsConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.ServerLogRepository
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.services.shell.BYTES_PER_KIB
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellAuthorizationRequest
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellElevation
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellLaunch
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellProcessPage
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellProcessState
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellScriptBuilder
import com.danielealbano.androidremotecontrolmcp.services.storage.FileOperationProvider
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.security.MessageDigest

private const val MILLIS_PER_SECOND = 1000L

/** Working directory for root tool commands: always exists, needs no storage permission. */
private const val ROOT_WORKDIR = "/"

private const val MAX_LOGCAT_LINES = 5000
private const val DEFAULT_LOGCAT_LINES = 200
private const val MAX_FILTER_CHARS = 200

internal object RootToolNames {
    const val INSTALL_APK = "install_apk"
    const val UNINSTALL_APP = "uninstall_app"
    const val REBOOT_DEVICE = "reboot_device"
    const val READ_LOGCAT = "read_logcat"
    const val READ_ROOT_FILE = "read_root_file"
    const val TYPE_VIRTUAL_KEYBOARD = "type_virtual_keyboard"
}

private val PACKAGE_ID_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*$")

/**
 * Runs one command through the standard elevated shell pipeline, exactly like an elevated
 * `shell_exec`: the [ShellToolContext.authorizer] checks the blocklist, requires elevation to be
 * enabled in the Shell settings, and either auto-allows, matches the allowlist, or asks the user —
 * with the [justification] shown to them and logged. There is no path around that authorization:
 * a command the authorizer refuses (or grants no elevation for) never reaches the process manager.
 */
internal class ElevatedCommandRunner(
    private val context: ShellToolContext,
) {
    suspend fun run(
        command: String,
        justification: String,
    ): ShellProcessPage {
        val mark = context.timeSource.markNow()
        val config = context.settingsRepository.getServerConfig().shellConfig
        val request = ShellAuthorizationRequest(command, justification, elevated = true)
        val budgetMs = config.limit(ShellLimit.FOREGROUND_WAIT_SECONDS) * MILLIS_PER_SECOND
        val grant =
            context.authorizer.authorize(request, config, budgetMs)
                ?: throw McpToolException.PermissionDenied(
                    "Refused: the command was not granted elevation.",
                )
        val launch =
            ShellLaunch(
                command = command,
                workdir = ROOT_WORKDIR,
                envs = emptyMap(),
                timeoutMinutes = config.backgroundTimeoutDefaultMinutes,
                elevation = ShellElevation(justification, grant),
            )
        val waitMs = budgetMs - mark.elapsedNow().inWholeMilliseconds
        val perCallBytes = config.limit(ShellLimit.OUTPUT_PER_CALL_KIB) * BYTES_PER_KIB
        return context.processManager.startAndAwait(launch, config, waitMs, perCallBytes)
    }
}

/** The justification every root tool requires: non-blank, bounded, NUL-free, as for elevated shell commands. */
internal fun requireJustification(arguments: JsonObject?): String {
    val value = McpToolUtils.optionalString(arguments, "justification", "").trim()
    if (value.isEmpty()) {
        throw McpToolException.InvalidParams("Parameter 'justification' is required for root tools")
    }
    if (value.length > ShellToolParams.MAX_JUSTIFICATION_CHARS) {
        throw McpToolException.InvalidParams(
            "Parameter 'justification' must be at most ${ShellToolParams.MAX_JUSTIFICATION_CHARS} characters, " +
                "got: ${value.length}",
        )
    }
    if (ShellScriptBuilder.hasNul(value)) {
        throw McpToolException.InvalidParams("Parameter 'justification' must not contain NUL characters")
    }
    return value
}

/** An absolute, NUL-free path argument (the value is shell-quoted again where the command is built). */
internal fun requireAbsolutePath(
    arguments: JsonObject?,
    name: String,
): String {
    val value = McpToolUtils.requireString(arguments, name)
    if (!value.startsWith("/") || ShellScriptBuilder.hasNul(value)) {
        throw McpToolException.InvalidParams(
            "Parameter '$name' must be an absolute path without NUL characters, got: '$value'",
        )
    }
    return value
}

/** Adds the shared required 'justification' property to a root tool's input schema. */
private fun JsonObjectBuilder.putJustificationProperty() {
    putJsonObject("justification") {
        put("type", "string")
        put(
            "description",
            "Why this root operation is needed (required). Shown to the user in the approval " +
                "request when approval is needed, and recorded in the server log.",
        )
    }
}

/** The command's output, or a failure carrying it: used by tools whose command reports through its output. */
private fun ShellProcessPage.outputOrFailure(toolAction: String): String {
    if (snapshot.state == ShellProcessState.FAILED) {
        throw McpToolException.ActionFailed("$toolAction failed to run: ${snapshot.failure ?: "unknown error"}")
    }
    if (snapshot.state != ShellProcessState.EXITED) {
        throw McpToolException.ActionFailed(
            "$toolAction did not finish within the foreground limit (process_id ${snapshot.id} keeps running)",
        )
    }
    return chunk.text
}

/**
 * MCP tool: install_apk
 *
 * Installs (or reinstalls/updates) the APK file at a path on the device with `pm install -r`,
 * run as root through the standard elevation authorization.
 */
internal class InstallApkHandler(
    private val runner: ElevatedCommandRunner,
) {
    suspend fun execute(arguments: JsonObject?): CallToolResult {
        val apkPath = requireAbsolutePath(arguments, "apk_path")
        if (!apkPath.endsWith(".apk", ignoreCase = true)) {
            throw McpToolException.InvalidParams("Parameter 'apk_path' must point to an .apk file, got: '$apkPath'")
        }
        val justification = requireJustification(arguments)

        val page = runner.run("pm install -r ${ShellScriptBuilder.quoteForShell(apkPath)}", justification)
        val output = page.outputOrFailure("Installing '$apkPath'")
        return if (output.lineSequence().any { it.trim() == "Success" }) {
            McpToolUtils.untrustedTextResult("Installed '$apkPath' successfully.\npm output: ${output.trim()}")
        } else {
            throw McpToolException.ActionFailed("pm install failed for '$apkPath': ${output.trim()}")
        }
    }

    fun register(
        registrar: LoggedToolRegistrar,
        toolNamePrefix: String,
    ) {
        registrar.addTool(
            toolName = RootToolNames.INSTALL_APK,
            name = "$toolNamePrefix${RootToolNames.INSTALL_APK}",
            description =
                "Install an APK file that is already on the device (pm install -r: an existing app is " +
                    "updated in place), running as root. Goes through the same elevation authorization as " +
                    "an elevated shell command: elevation must be enabled in the app's Shell settings, and " +
                    "unless auto-allow is on or the command is allowlisted, the user approves it on the " +
                    "phone. Returns the pm output; a failed install is a tool error carrying that output.",
            inputSchema =
                ToolSchema(
                    properties =
                        buildJsonObject {
                            putJsonObject("apk_path") {
                                put("type", "string")
                                put("description", "Absolute path of the .apk file on the device")
                            }
                            putJustificationProperty()
                        },
                    required = listOf("apk_path", "justification"),
                ),
        ) { request -> execute(request.arguments) }
    }
}

/**
 * MCP tool: uninstall_app
 *
 * Uninstalls an app by package id with `pm uninstall`, run as root through the standard elevation
 * authorization.
 */
internal class UninstallAppHandler(
    private val runner: ElevatedCommandRunner,
) {
    suspend fun execute(arguments: JsonObject?): CallToolResult {
        val packageId = McpToolUtils.requireString(arguments, "package_id")
        if (!PACKAGE_ID_PATTERN.matches(packageId)) {
            throw McpToolException.InvalidParams("Parameter 'package_id' is not a valid package id: '$packageId'")
        }
        val justification = requireJustification(arguments)

        val page = runner.run("pm uninstall ${ShellScriptBuilder.quoteForShell(packageId)}", justification)
        val output = page.outputOrFailure("Uninstalling '$packageId'")
        return if (output.lineSequence().any { it.trim() == "Success" }) {
            McpToolUtils.untrustedTextResult("Uninstalled '$packageId' successfully.\npm output: ${output.trim()}")
        } else {
            throw McpToolException.ActionFailed("pm uninstall failed for '$packageId': ${output.trim()}")
        }
    }

    fun register(
        registrar: LoggedToolRegistrar,
        toolNamePrefix: String,
    ) {
        registrar.addTool(
            toolName = RootToolNames.UNINSTALL_APP,
            name = "$toolNamePrefix${RootToolNames.UNINSTALL_APP}",
            description =
                "Uninstall an app by its package id (pm uninstall), running as root. Goes through the " +
                    "same elevation authorization as an elevated shell command: elevation must be enabled " +
                    "in the app's Shell settings, and unless auto-allow is on or the command is " +
                    "allowlisted, the user approves it on the phone. Returns the pm output; a failed " +
                    "uninstall is a tool error carrying that output.",
            inputSchema =
                ToolSchema(
                    properties =
                        buildJsonObject {
                            putJsonObject("package_id") {
                                put("type", "string")
                                put("description", "Package id of the app to uninstall, e.g. com.example.app")
                            }
                            putJustificationProperty()
                        },
                    required = listOf("package_id", "justification"),
                ),
        ) { request -> execute(request.arguments) }
    }
}

/**
 * MCP tool: reboot_device
 *
 * Reboots the device with `reboot`, run as root through the standard elevation authorization. The
 * request is written to the server log before the command runs, so the log keeps the reason even
 * though the reboot takes the server down with the device.
 */
internal class RebootDeviceHandler(
    private val runner: ElevatedCommandRunner,
    private val serverLogRepository: ServerLogRepository,
) {
    suspend fun execute(arguments: JsonObject?): CallToolResult {
        val justification = requireJustification(arguments)

        serverLogRepository.log(
            ServerLogEntry.Type.SHELL,
            "Device reboot requested through the reboot_device tool | justification: $justification",
        )
        val page = runner.run("reboot", justification)
        if (page.snapshot.state == ShellProcessState.FAILED) {
            throw McpToolException.ActionFailed(
                "The reboot command failed to run: ${page.snapshot.failure ?: "unknown error"}",
            )
        }
        return McpToolUtils.untrustedTextResult(
            "Reboot command authorized and started (process_id ${page.snapshot.id}). " +
                "The device will now restart and this MCP connection will drop.",
        )
    }

    fun register(
        registrar: LoggedToolRegistrar,
        toolNamePrefix: String,
    ) {
        registrar.addTool(
            toolName = RootToolNames.REBOOT_DEVICE,
            name = "$toolNamePrefix${RootToolNames.REBOOT_DEVICE}",
            description =
                "Reboot the device, running as root. Goes through the same elevation authorization as " +
                    "an elevated shell command: elevation must be enabled in the app's Shell settings, " +
                    "and unless auto-allow is on or the command is allowlisted, the user approves it on " +
                    "the phone. The request and its justification are written to the server log before " +
                    "the reboot command runs. The MCP connection drops as the device restarts.",
            inputSchema =
                ToolSchema(
                    properties =
                        buildJsonObject {
                            putJustificationProperty()
                        },
                    required = listOf("justification"),
                ),
        ) { request -> execute(request.arguments) }
    }
}

/**
 * MCP tool: read_logcat
 *
 * Reads the device log buffers with `logcat -d`, run as root through the standard elevation
 * authorization (logcat as root also sees other processes' logs). For diagnosing failures on the
 * device itself.
 */
internal class ReadLogcatHandler(
    private val runner: ElevatedCommandRunner,
) {
    suspend fun execute(arguments: JsonObject?): CallToolResult {
        val lines = McpToolUtils.optionalInt(arguments, "lines", DEFAULT_LOGCAT_LINES)
        if (lines !in 1..MAX_LOGCAT_LINES) {
            throw McpToolException.InvalidParams(
                "Parameter 'lines' must be between 1 and $MAX_LOGCAT_LINES, got: $lines",
            )
        }
        val filter = McpToolUtils.optionalString(arguments, "filter", "").trim()
        if (filter.length > MAX_FILTER_CHARS || ShellScriptBuilder.hasNul(filter)) {
            throw McpToolException.InvalidParams(
                "Parameter 'filter' must be at most $MAX_FILTER_CHARS characters without NUL characters",
            )
        }
        val justification = requireJustification(arguments)

        val command =
            if (filter.isEmpty()) {
                "logcat -d -t $lines"
            } else {
                "logcat -d -t $lines | grep -F -e ${ShellScriptBuilder.quoteForShell(filter)}"
            }
        val page = runner.run(command, justification)
        val output = page.outputOrFailure("Reading the logcat buffer")
        val header =
            if (filter.isEmpty()) {
                "Last $lines log lines:"
            } else {
                "Last $lines log lines containing '$filter':"
            }
        val body =
            buildString {
                append(header)
                append('\n')
                append(output.ifBlank { "(no log lines)" })
                if (page.chunk.nextOffset < page.chunk.totalBytes) {
                    append("\n--- Output truncated at the per-call byte limit; narrow the filter or read fewer lines. ---")
                }
            }
        return McpToolUtils.untrustedTextResult(body)
    }

    fun register(
        registrar: LoggedToolRegistrar,
        toolNamePrefix: String,
    ) {
        registrar.addTool(
            toolName = RootToolNames.READ_LOGCAT,
            name = "$toolNamePrefix${RootToolNames.READ_LOGCAT}",
            description =
                "Read the device's logcat buffers (dumped, not followed), running as root so other " +
                    "processes' logs are visible too. Goes through the same elevation authorization as " +
                    "an elevated shell command. 'lines' caps how many of the most recent lines are read " +
                    "(default $DEFAULT_LOGCAT_LINES, max $MAX_LOGCAT_LINES); 'filter' keeps only lines " +
                    "containing it as a fixed string (a tag or keyword). Output is still bounded by the " +
                    "Shell settings per-call byte limit.",
            inputSchema =
                ToolSchema(
                    properties =
                        buildJsonObject {
                            putJsonObject("lines") {
                                put("type", "integer")
                                put(
                                    "description",
                                    "How many of the most recent log lines to read " +
                                        "(default $DEFAULT_LOGCAT_LINES, max $MAX_LOGCAT_LINES)",
                                )
                            }
                            putJsonObject("filter") {
                                put("type", "string")
                                put(
                                    "description",
                                    "Optional fixed string (tag or keyword); only lines containing it are returned",
                                )
                            }
                            putJustificationProperty()
                        },
                    required = listOf("justification"),
                ),
        ) { request -> execute(request.arguments) }
    }
}

/**
 * MCP tool: read_root_file
 *
 * Reads a text file at any absolute path with `cat`-equivalent shell reads, run as root through the
 * standard elevation authorization — unlike read_file, which is limited to authorized storage
 * locations, this reaches app-private data and system files. Presentation follows read_file:
 * line-numbered output, offset/limit pagination capped at [FileOperationProvider.MAX_READ_LINES]
 * lines per call, and the content decoded as UTF-8 text (binary content decodes with replacement
 * characters, as in read_file).
 */
internal class ReadRootFileHandler(
    private val runner: ElevatedCommandRunner,
) {
    suspend fun execute(arguments: JsonObject?): CallToolResult {
        val path = requireAbsolutePath(arguments, "path")
        val offset = McpToolUtils.optionalInt(arguments, "offset", 1)
        if (offset < 1) {
            throw McpToolException.InvalidParams("Parameter 'offset' must be >= 1, got: $offset")
        }
        val limit = McpToolUtils.optionalInt(arguments, "limit", FileOperationProvider.MAX_READ_LINES)
        if (limit !in 1..FileOperationProvider.MAX_READ_LINES) {
            throw McpToolException.InvalidParams(
                "Parameter 'limit' must be between 1 and ${FileOperationProvider.MAX_READ_LINES}, got: $limit",
            )
        }
        val justification = requireJustification(arguments)

        val quoted = ShellScriptBuilder.quoteForShell(path)
        val end = offset + limit - 1
        val command = "[ -f $quoted ] || exit 3; wc -l < $quoted; sed -n '${offset},${end}p' $quoted"
        val page = runner.run(command, justification)
        if (page.snapshot.state == ShellProcessState.EXITED && page.snapshot.exitCode == 3) {
            throw McpToolException.ActionFailed("'$path' is not a regular file (or is not readable as root)")
        }
        val output = page.outputOrFailure("Reading '$path'")
        val rawLines = output.split('\n')
        val totalLines =
            rawLines.firstOrNull()?.trim()?.toIntOrNull()
                ?: throw McpToolException.ActionFailed("Could not read '$path': unexpected output: ${output.take(200)}")
        // The first line is the wc count; the rest is the requested window (a trailing newline
        // leaves one empty tail segment that is not a file line).
        var contentLines = rawLines.drop(1)
        if (contentLines.lastOrNull() == "") {
            contentLines = contentLines.dropLast(1)
        }

        val text =
            buildString {
                contentLines.forEachIndexed { index, line ->
                    if (index > 0) append('\n')
                    append("${offset + index}| $line")
                }
                val lastShown = offset + contentLines.size - 1
                if (contentLines.isNotEmpty() && lastShown < totalLines) {
                    append(
                        "\n--- More lines available. Use offset=${lastShown + 1} " +
                            "to continue reading. Total lines: $totalLines ---",
                    )
                }
            }
        return McpToolUtils.untrustedTextResult(text)
    }

    fun register(
        registrar: LoggedToolRegistrar,
        toolNamePrefix: String,
    ) {
        registrar.addTool(
            toolName = RootToolNames.READ_ROOT_FILE,
            name = "$toolNamePrefix${RootToolNames.READ_ROOT_FILE}",
            description =
                "Read a text file at any absolute path on the device, running as root — including " +
                    "app-private data and system files that read_file cannot reach (it is limited to " +
                    "authorized storage locations). Goes through the same elevation authorization as an " +
                    "elevated shell command. Returns content with line numbers, paginated like " +
                    "read_file: 'offset' is the 1-based first line (default 1), 'limit' the line count " +
                    "(default and max ${FileOperationProvider.MAX_READ_LINES}). Content is decoded as " +
                    "UTF-8 text, like read_file.",
            inputSchema =
                ToolSchema(
                    properties =
                        buildJsonObject {
                            putJsonObject("path") {
                                put("type", "string")
                                put("description", "Absolute path of the file to read")
                            }
                            putJsonObject("offset") {
                                put("type", "integer")
                                put("description", "1-based line number to start reading from (default 1)")
                            }
                            putJsonObject("limit") {
                                put("type", "integer")
                                put(
                                    "description",
                                    "Maximum number of lines to return " +
                                        "(default and max ${FileOperationProvider.MAX_READ_LINES})",
                                )
                            }
                            putJustificationProperty()
                        },
                    required = listOf("path", "justification"),
                ),
        ) { request -> execute(request.arguments) }
    }
}

private const val MAX_VIRTUAL_KEYBOARD_TEXT_LENGTH = 500
private const val VIRTUAL_KEYBOARD_ASSET = "vkbd"
private const val VIRTUAL_KEYBOARD_BINARY_NAME = "vkbd"
private const val VIRTUAL_KEYBOARD_INPUT_NAME = "vkbd-input.txt"

/**
 * MCP tool: type_virtual_keyboard
 *
 * Types text into the currently focused field as a virtual USB keyboard created through
 * /dev/uinput, running as root through the standard elevation authorization. The key events
 * travel the input pipeline exactly like a hardware keyboard, so they also reach fields that
 * only accept IME-committed text — notably WebView inputs while every soft keyboard is
 * disabled, where edit_text (an accessibility content set) cannot type. The text itself never
 * appears on the command line, in the result or in the server log: the handler writes it to a
 * file in the app's private storage and the bundled vkbd helper (assets/vkbd, source in
 * scripts/vkbd/) reads it from stdin. Only printable ASCII and '\n' (typed as Enter) can be
 * typed; any other character is rejected up front so a call never silently drops part of the
 * text.
 */
internal class TypeVirtualKeyboardHandler(
    private val appContext: Context,
    private val runner: ElevatedCommandRunner,
) {
    suspend fun execute(arguments: JsonObject?): CallToolResult {
        val text = McpToolUtils.requireString(arguments, "text")
        if (text.isEmpty()) {
            throw McpToolException.InvalidParams("Parameter 'text' must be non-empty")
        }
        if (text.length > MAX_VIRTUAL_KEYBOARD_TEXT_LENGTH) {
            throw McpToolException.InvalidParams(
                "Parameter 'text' must be at most $MAX_VIRTUAL_KEYBOARD_TEXT_LENGTH characters, got: ${text.length}",
            )
        }
        if (text.any { it.code !in ' '.code..'~'.code && it != '\n' }) {
            throw McpToolException.InvalidParams(
                "Parameter 'text' contains characters the virtual keyboard cannot type: " +
                    "only printable ASCII and newline are supported",
            )
        }
        val justification = requireJustification(arguments)

        val binary = ensureVirtualKeyboardBinary()
        val inputFile = File(appContext.filesDir, VIRTUAL_KEYBOARD_INPUT_NAME)
        inputFile.writeText(text, Charsets.UTF_8)
        val command =
            "${ShellScriptBuilder.quoteForShell(binary.absolutePath)} - < " +
                ShellScriptBuilder.quoteForShell(inputFile.absolutePath)
        val page = runner.run(command, justification)
        val output = page.outputOrFailure("Typing with the virtual keyboard")
        if (page.snapshot.exitCode != 0) {
            throw McpToolException.ActionFailed(
                "The virtual keyboard helper failed (exit code ${page.snapshot.exitCode}): ${output.trim()}",
            )
        }
        return McpToolUtils.textResult("Typed ${text.length} characters with the virtual keyboard.")
    }

    /**
     * Extracts the bundled vkbd helper into the app's private storage and returns it. Keyed on
     * the asset's SHA-256, so an app update shipping a new helper replaces the old copy while
     * repeat calls only cost one hash of each side.
     */
    private fun ensureVirtualKeyboardBinary(): File {
        val target = File(appContext.filesDir, VIRTUAL_KEYBOARD_BINARY_NAME)
        val bytes = appContext.assets.open(VIRTUAL_KEYBOARD_ASSET).use { it.readBytes() }
        if (!target.isFile || sha256(target.readBytes()) != sha256(bytes)) {
            target.writeBytes(bytes)
        }
        target.setExecutable(true, false)
        return target
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    fun register(
        registrar: LoggedToolRegistrar,
        toolNamePrefix: String,
    ) {
        registrar.addTool(
            toolName = RootToolNames.TYPE_VIRTUAL_KEYBOARD,
            name = "$toolNamePrefix${RootToolNames.TYPE_VIRTUAL_KEYBOARD}",
            description =
                "Type text into the currently focused field as a virtual USB keyboard (root, " +
                    "via /dev/uinput): the key events travel the input pipeline like a hardware " +
                    "keyboard, so they reach fields that only accept IME-committed text — " +
                    "notably WebView inputs while every soft keyboard is disabled, where " +
                    "edit_text cannot type. No soft keyboard is shown. The field must already " +
                    "be focused (tap it first). Only printable ASCII and newline (typed as " +
                    "Enter) are supported; max $MAX_VIRTUAL_KEYBOARD_TEXT_LENGTH characters " +
                    "per call — split longer text across calls. Goes through the same elevation " +
                    "authorization as an elevated shell command. The typed text is never " +
                    "echoed back.",
            inputSchema =
                ToolSchema(
                    properties =
                        buildJsonObject {
                            putJsonObject("text") {
                                put("type", "string")
                                put(
                                    "description",
                                    "Text to type (printable ASCII and newline only, " +
                                        "max $MAX_VIRTUAL_KEYBOARD_TEXT_LENGTH characters)",
                                )
                            }
                            putJustificationProperty()
                        },
                    required = listOf("text", "justification"),
                ),
        ) { request -> execute(request.arguments) }
    }
}

fun registerRootTools(
    registrar: LoggedToolRegistrar,
    context: ShellToolContext,
    appContext: Context,
    serverLogRepository: ServerLogRepository,
    toolNamePrefix: String,
    perms: ToolPermissionsConfig,
) {
    val runner = ElevatedCommandRunner(context)
    if (perms.isToolEnabled(RootToolNames.INSTALL_APK)) {
        InstallApkHandler(runner).register(registrar, toolNamePrefix)
    }
    if (perms.isToolEnabled(RootToolNames.UNINSTALL_APP)) {
        UninstallAppHandler(runner).register(registrar, toolNamePrefix)
    }
    if (perms.isToolEnabled(RootToolNames.REBOOT_DEVICE)) {
        RebootDeviceHandler(runner, serverLogRepository).register(registrar, toolNamePrefix)
    }
    if (perms.isToolEnabled(RootToolNames.READ_LOGCAT)) {
        ReadLogcatHandler(runner).register(registrar, toolNamePrefix)
    }
    if (perms.isToolEnabled(RootToolNames.READ_ROOT_FILE)) {
        ReadRootFileHandler(runner).register(registrar, toolNamePrefix)
    }
    if (perms.isToolEnabled(RootToolNames.TYPE_VIRTUAL_KEYBOARD)) {
        TypeVirtualKeyboardHandler(appContext, runner).register(registrar, toolNamePrefix)
    }
}

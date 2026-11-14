package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.data.model.ToolPermissionsConfig
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellProcessPage
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellProcessState
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellScriptBuilder
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

internal object FileTransferToolNames {
    const val WRITE_FILE_CHUNK = "write_file_chunk"
    const val READ_FILE_CHUNK = "read_file_chunk"
    const val FILE_INFO = "file_info"
}

/**
 * Per-call transfer limits. One MCP call must stay far below the transport payload limits
 * (the phone is reached over a Cloudflare tunnel whose JSON-RPC messages cannot grow without
 * bound), so a chunk is capped at 128 KiB of raw bytes (~175K base64 characters) no matter what
 * the caller asks for; the default chunk is a conservative 24 KiB (~32K base64 characters).
 * Larger files are moved as a sequence of chunks at increasing offsets.
 */
internal object FileTransferLimits {
    const val MAX_CHUNK_BYTES = 131_072
    const val DEFAULT_CHUNK_BYTES = 24_576
}

private fun JsonObjectBuilder.putJustificationProperty() {
    putJsonObject("justification") {
        put("type", "string")
        put(
            "description",
            "Why this file transfer is needed (required). Shown to the user in the approval " +
                "request when approval is needed, and recorded in the server log.",
        )
    }
}

private fun JsonObjectBuilder.putPathProperty() {
    putJsonObject("path") {
        put("type", "string")
        put("description", "Absolute path of the file on the device")
    }
}

private fun JsonObjectBuilder.putOffsetProperty() {
    putJsonObject("offset") {
        put("type", "integer")
        put("description", "Byte offset in the file (0-based, default 0)")
    }
}

/** The command's output, or a failure carrying it — mirrors the root tools' presentation. */
private fun ShellProcessPage.chunkOutputOrFailure(toolAction: String): String {
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

/** Decodes a base64 argument strictly; the decoded size is the caller's chunk, already capped. */
private fun decodeBase64Chunk(value: String): ByteArray {
    val compact = value.filterNot { it.isWhitespace() }
    val bytes =
        try {
            Base64.getDecoder().decode(compact)
        } catch (e: IllegalArgumentException) {
            throw McpToolException.InvalidParams("Parameter 'data_base64' is not valid base64")
        }
    if (bytes.size > FileTransferLimits.MAX_CHUNK_BYTES) {
        throw McpToolException.InvalidParams(
            "Parameter 'data_base64' decodes to ${bytes.size} bytes, over the per-call cap of " +
                "${FileTransferLimits.MAX_CHUNK_BYTES} bytes; send the file in smaller chunks",
        )
    }
    return bytes
}

/**
 * MCP tool: write_file_chunk
 *
 * Writes one base64 chunk of a binary file at an absolute path and byte offset, as root through
 * the standard elevation authorization. Stateless: each call is independent, so callers upload a
 * file of any size as a sequence of chunks (first call with truncate=true), retry a failed chunk
 * on its own, and verify the result with file_info. The chunk is decoded by `base64 -d` and
 * placed with `dd ... conv=notrunc`, so existing bytes outside the chunk are preserved.
 */
internal class WriteFileChunkHandler(
    private val runner: ElevatedCommandRunner,
) {
    suspend fun execute(arguments: JsonObject?): CallToolResult {
        val path = requireAbsolutePath(arguments, "path")
        val offset = McpToolUtils.optionalLong(arguments, "offset", 0L)
        if (offset < 0) {
            throw McpToolException.InvalidParams("Parameter 'offset' must be >= 0, got: $offset")
        }
        val bytes = decodeBase64Chunk(McpToolUtils.requireString(arguments, "data_base64"))
        val truncate = McpToolUtils.optionalBoolean(arguments, "truncate", false)
        val justification = requireJustification(arguments)

        val quotedPath = ShellScriptBuilder.quoteForShell(path)
        val parent = path.substringBeforeLast('/', "").ifEmpty { "/" }
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val command =
            buildString {
                append("mkdir -p ${ShellScriptBuilder.quoteForShell(parent)}")
                if (truncate) {
                    append(" && : > $quotedPath")
                }
                append(" && printf '%s' '$encoded' | base64 -d")
                append(" | dd of=$quotedPath bs=1 seek=$offset conv=notrunc 2>/dev/null")
                append(" && stat -c %s $quotedPath")
            }
        val page = runner.run(command, justification)
        val output = page.chunkOutputOrFailure("Writing a chunk to '$path'")
        val fileSize =
            output.trim().lines().lastOrNull()?.toLongOrNull()
                ?: throw McpToolException.ActionFailed("Could not write '$path': unexpected output: ${output.take(200)}")

        val resultJson =
            buildJsonObject {
                put("path", path)
                put("offset", offset)
                put("bytes_written", bytes.size)
                put("file_size", fileSize)
            }
        return McpToolUtils.textResult(Json.encodeToString(resultJson))
    }

    fun register(
        registrar: LoggedToolRegistrar,
        toolNamePrefix: String,
    ) {
        registrar.addTool(
            toolName = FileTransferToolNames.WRITE_FILE_CHUNK,
            name = "$toolNamePrefix${FileTransferToolNames.WRITE_FILE_CHUNK}",
            description =
                "Write one chunk of a binary file at any absolute path, running as root through the " +
                    "same elevation authorization as an elevated shell command. 'data_base64' is the " +
                    "chunk (at most ${FileTransferLimits.MAX_CHUNK_BYTES} raw bytes per call, so one " +
                    "call always stays well below transport payload limits); it is written at " +
                    "'offset' without touching other bytes. Pass truncate=true on the first chunk to " +
                    "create/replace the file. Upload a whole file as a sequence of chunks, then check " +
                    "it with file_info. Returns JSON: path, offset, bytes_written, file_size.",
            inputSchema =
                ToolSchema(
                    properties =
                        buildJsonObject {
                            putPathProperty()
                            putOffsetProperty()
                            putJsonObject("data_base64") {
                                put("type", "string")
                                put(
                                    "description",
                                    "The chunk bytes, base64-encoded " +
                                        "(max ${FileTransferLimits.MAX_CHUNK_BYTES} raw bytes)",
                                )
                            }
                            putJsonObject("truncate") {
                                put("type", "boolean")
                                put(
                                    "description",
                                    "Truncate the file to zero length before writing (default false); " +
                                        "use on the first chunk of an upload",
                                )
                                put("default", false)
                            }
                            putJustificationProperty()
                        },
                    required = listOf("path", "data_base64", "justification"),
                ),
        ) { request -> execute(request.arguments) }
    }
}

/**
 * MCP tool: read_file_chunk
 *
 * Reads one chunk of a binary file at an absolute path and byte offset, as root through the
 * standard elevation authorization, and returns it base64-encoded together with the total file
 * size. 'length' is clamped to the per-call cap server-side, so a caller asking for more still
 * gets a bounded response; download a whole file as a sequence of chunks until 'eof' is true,
 * then verify it with file_info.
 */
internal class ReadFileChunkHandler(
    private val runner: ElevatedCommandRunner,
) {
    suspend fun execute(arguments: JsonObject?): CallToolResult {
        val path = requireAbsolutePath(arguments, "path")
        val offset = McpToolUtils.optionalLong(arguments, "offset", 0L)
        if (offset < 0) {
            throw McpToolException.InvalidParams("Parameter 'offset' must be >= 0, got: $offset")
        }
        val requestedLength = McpToolUtils.optionalInt(arguments, "length", FileTransferLimits.DEFAULT_CHUNK_BYTES)
        if (requestedLength < 1) {
            throw McpToolException.InvalidParams("Parameter 'length' must be >= 1, got: $requestedLength")
        }
        val length = minOf(requestedLength, FileTransferLimits.MAX_CHUNK_BYTES)
        val justification = requireJustification(arguments)

        val quotedPath = ShellScriptBuilder.quoteForShell(path)
        val command =
            "[ -f $quotedPath ] || exit 3; stat -c %s $quotedPath; " +
                "dd if=$quotedPath bs=1 skip=$offset count=$length 2>/dev/null | base64"
        val page = runner.run(command, justification)
        if (page.snapshot.state == ShellProcessState.EXITED && page.snapshot.exitCode == 3) {
            throw McpToolException.ActionFailed("'$path' is not a regular file (or is not readable as root)")
        }
        val output = page.chunkOutputOrFailure("Reading a chunk of '$path'")
        val lines = output.split('\n')
        val fileSize =
            lines.firstOrNull()?.trim()?.toLongOrNull()
                ?: throw McpToolException.ActionFailed("Could not read '$path': unexpected output: ${output.take(200)}")
        val encoded = lines.drop(1).joinToString("").filterNot { it.isWhitespace() }
        val bytes =
            try {
                if (encoded.isEmpty()) ByteArray(0) else Base64.getDecoder().decode(encoded)
            } catch (e: IllegalArgumentException) {
                throw McpToolException.ActionFailed("Could not read '$path': the device returned invalid base64")
            }

        val resultJson =
            buildJsonObject {
                put("path", path)
                put("offset", offset)
                put("file_size", fileSize)
                put("bytes_returned", bytes.size)
                put("eof", offset + bytes.size >= fileSize)
                put("data_base64", Base64.getEncoder().encodeToString(bytes))
            }
        return McpToolUtils.untrustedTextResult(Json.encodeToString(resultJson))
    }

    fun register(
        registrar: LoggedToolRegistrar,
        toolNamePrefix: String,
    ) {
        registrar.addTool(
            toolName = FileTransferToolNames.READ_FILE_CHUNK,
            name = "$toolNamePrefix${FileTransferToolNames.READ_FILE_CHUNK}",
            description =
                "Read one chunk of a binary file at any absolute path, running as root through the " +
                    "same elevation authorization as an elevated shell command. Returns JSON with " +
                    "the chunk as data_base64, the total file_size, bytes_returned and eof. 'length' " +
                    "defaults to ${FileTransferLimits.DEFAULT_CHUNK_BYTES} and is clamped to " +
                    "${FileTransferLimits.MAX_CHUNK_BYTES} raw bytes per call, so one call always " +
                    "stays well below transport payload limits. Download a whole file as a sequence " +
                    "of chunks until eof, then verify it with file_info.",
            inputSchema =
                ToolSchema(
                    properties =
                        buildJsonObject {
                            putPathProperty()
                            putOffsetProperty()
                            putJsonObject("length") {
                                put("type", "integer")
                                put(
                                    "description",
                                    "How many bytes to read (default ${FileTransferLimits.DEFAULT_CHUNK_BYTES}, " +
                                        "clamped to ${FileTransferLimits.MAX_CHUNK_BYTES})",
                                )
                            }
                            putJustificationProperty()
                        },
                    required = listOf("path", "justification"),
                ),
        ) { request -> execute(request.arguments) }
    }
}

/**
 * MCP tool: file_info
 *
 * Reports a file's existence, size and SHA-256 hash at any absolute path, in one elevated call,
 * so a caller can verify a chunked upload or download end to end without extra round trips.
 */
internal class FileInfoHandler(
    private val runner: ElevatedCommandRunner,
) {
    suspend fun execute(arguments: JsonObject?): CallToolResult {
        val path = requireAbsolutePath(arguments, "path")
        val justification = requireJustification(arguments)

        val quotedPath = ShellScriptBuilder.quoteForShell(path)
        val command =
            "if [ -f $quotedPath ]; then echo EXISTS; stat -c %s $quotedPath; sha256sum $quotedPath; " +
                "else echo MISSING; fi"
        val page = runner.run(command, justification)
        val output = page.chunkOutputOrFailure("Inspecting '$path'")
        val lines = output.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

        val resultJson =
            when (lines.firstOrNull()) {
                "MISSING" ->
                    buildJsonObject {
                        put("path", path)
                        put("exists", false)
                    }
                "EXISTS" -> {
                    val size =
                        lines.getOrNull(1)?.toLongOrNull()
                            ?: throw McpToolException.ActionFailed(
                                "Could not inspect '$path': unexpected output: ${output.take(200)}",
                            )
                    val sha256 = lines.getOrNull(2)?.split(Regex("\\s+"))?.firstOrNull().orEmpty()
                    if (!SHA256_HEX_PATTERN.matches(sha256)) {
                        throw McpToolException.ActionFailed(
                            "Could not inspect '$path': unexpected sha256sum output: ${output.take(200)}",
                        )
                    }
                    buildJsonObject {
                        put("path", path)
                        put("exists", true)
                        put("size", size)
                        put("sha256", sha256)
                    }
                }
                else ->
                    throw McpToolException.ActionFailed(
                        "Could not inspect '$path': unexpected output: ${output.take(200)}",
                    )
            }
        return McpToolUtils.untrustedTextResult(Json.encodeToString(resultJson))
    }

    fun register(
        registrar: LoggedToolRegistrar,
        toolNamePrefix: String,
    ) {
        registrar.addTool(
            toolName = FileTransferToolNames.FILE_INFO,
            name = "$toolNamePrefix${FileTransferToolNames.FILE_INFO}",
            description =
                "Report whether a file exists at any absolute path, and if so its size in bytes " +
                    "and SHA-256 hash, in a single call running as root through the same elevation " +
                    "authorization as an elevated shell command. Use it before and after chunked " +
                    "transfers (write_file_chunk / read_file_chunk) to verify integrity.",
            inputSchema =
                ToolSchema(
                    properties =
                        buildJsonObject {
                            putPathProperty()
                            putJustificationProperty()
                        },
                    required = listOf("path", "justification"),
                ),
        ) { request -> execute(request.arguments) }
    }

    private companion object {
        val SHA256_HEX_PATTERN = Regex("^[0-9a-f]{64}$")
    }
}

fun registerFileTransferTools(
    registrar: LoggedToolRegistrar,
    context: ShellToolContext,
    toolNamePrefix: String,
    perms: ToolPermissionsConfig,
) {
    val runner = ElevatedCommandRunner(context)
    if (perms.isToolEnabled(FileTransferToolNames.WRITE_FILE_CHUNK)) {
        WriteFileChunkHandler(runner).register(registrar, toolNamePrefix)
    }
    if (perms.isToolEnabled(FileTransferToolNames.READ_FILE_CHUNK)) {
        ReadFileChunkHandler(runner).register(registrar, toolNamePrefix)
    }
    if (perms.isToolEnabled(FileTransferToolNames.FILE_INFO)) {
        FileInfoHandler(runner).register(registrar, toolNamePrefix)
    }
}

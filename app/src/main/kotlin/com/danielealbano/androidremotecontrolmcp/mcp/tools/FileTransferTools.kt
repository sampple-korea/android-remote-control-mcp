package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.data.model.ToolPermissionsConfig
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellProcessPage
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellProcessState
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellScriptBuilder
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

internal object FileTransferToolNames {
    const val WRITE_FILE_CHUNK = "write_file_chunk"
    const val READ_FILE_CHUNK = "read_file_chunk"
    const val FILE_INFO = "file_info"
}

/**
 * Per-call transfer limits. A chunk is capped at 1 MiB of raw bytes (~1.4M base64 characters):
 * large enough that a multi-megabyte file takes only a handful of calls, small enough that one
 * MCP call stays far below the transport payload limits (the phone is reached over a Cloudflare
 * tunnel). Larger files are moved as a sequence of chunks at increasing offsets. The default
 * chunk (when a reader omits 'length') stays a conservative 24 KiB.
 *
 * The cap used to be 128 KiB because the chunk itself travelled inside the elevated shell
 * command line (`printf '%s' '<base64>' | base64 -d | dd ...`), and the device's su layer
 * refuses to start a process whose command line is much longer than ~128K characters. Chunks no
 * longer touch a command line at all — see [DeviceFileChunkStore] — so the cap is set by the
 * transport, not by argv.
 */
internal object FileTransferLimits {
    const val MAX_CHUNK_BYTES = 1_048_576
    const val DEFAULT_CHUNK_BYTES = 24_576
}

/** Block sizes tried for the staging `dd`, largest first; the transfer block must divide the offset. */
private val TRANSFER_BLOCK_LADDER =
    longArrayOf(
        1_048_576, 524_288, 262_144, 131_072, 65_536, 32_768, 16_384, 8_192, 4_096, 2_048,
        1_024, 512, 256, 128, 64, 32, 16, 8, 4, 2,
    )

/**
 * The `dd` block size for a chunk at [offset]: the largest ladder entry dividing the offset
 * (1 MiB for offset 0), so `seek`/`skip` stay whole blocks while blocks stay large. The byte
 * count itself needs no divisibility — `dd` writes the final partial block as-is.
 */
internal fun transferBlockSize(offset: Long): Long {
    if (offset <= 0L) return TRANSFER_BLOCK_LADDER.first()
    for (block in TRANSFER_BLOCK_LADDER) {
        if (offset % block == 0L) return block
    }
    return 1L
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

/** One chunk read from a file: the bytes plus the file's total size. */
internal class FileChunkData(
    val bytes: ByteArray,
    val fileSize: Long,
)

/** A file's size and SHA-256, as reported by [FileChunkStore.fileInfo]. */
internal class FileInfoData(
    val size: Long,
    val sha256: String,
)

/**
 * The byte-moving backend behind the file transfer tools. Implementations move one chunk
 * without ever placing the chunk's bytes on a shell command line.
 */
internal interface FileChunkStore {
    /** Writes [bytes] at [offset] in the file at [path]; returns the file size afterwards. */
    suspend fun writeChunk(
        path: String,
        offset: Long,
        bytes: ByteArray,
        truncate: Boolean,
        justification: String,
    ): Long

    /** Reads up to [length] bytes at [offset] from the file at [path]. */
    suspend fun readChunk(
        path: String,
        offset: Long,
        length: Int,
        justification: String,
    ): FileChunkData

    /** Returns the file's size and SHA-256, or null when it does not exist. */
    suspend fun fileInfo(
        path: String,
        justification: String,
    ): FileInfoData?
}

/**
 * Moves chunks directly inside the server process wherever the app itself can reach the path
 * (its own storage), and stages them through the shell scratch directory everywhere else:
 * the decoded bytes are written to a scratch file in-process, then one short elevated `dd`
 * command — paths and numbers only — places them at the target offset (and the mirror image
 * for reads). The staged route goes through the standard elevation authorization with the
 * caller's justification, exactly like the other root tools; the direct route needs no
 * elevation because nothing is elevated, mirroring the non-root file tools.
 */
internal class DeviceFileChunkStore(
    context: ShellToolContext,
    private val scratchDir: File?,
) : FileChunkStore {
    private val runner = ElevatedCommandRunner(context)

    override suspend fun writeChunk(
        path: String,
        offset: Long,
        bytes: ByteArray,
        truncate: Boolean,
        justification: String,
    ): Long {
        directWrite(path, offset, bytes, truncate)?.let { return it }
        return stagedWrite(path, offset, bytes, truncate, justification)
    }

    override suspend fun readChunk(
        path: String,
        offset: Long,
        length: Int,
        justification: String,
    ): FileChunkData {
        directRead(path, offset, length)?.let { return it }
        return stagedRead(path, offset, length, justification)
    }

    override suspend fun fileInfo(
        path: String,
        justification: String,
    ): FileInfoData? {
        directInfo(path)?.let { return it }
        return rootInfo(path, justification)
    }

    // ─── Direct in-process IO (paths the app itself can reach) ───

    private suspend fun directWrite(
        path: String,
        offset: Long,
        bytes: ByteArray,
        truncate: Boolean,
    ): Long? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                file.parentFile?.mkdirs()
                RandomAccessFile(file, "rw").use { raf ->
                    if (truncate) raf.setLength(0)
                    raf.seek(offset)
                    raf.write(bytes)
                    raf.length()
                }
            } catch (e: IOException) {
                null
            } catch (e: SecurityException) {
                null
            }
        }

    private suspend fun directRead(
        path: String,
        offset: Long,
        length: Int,
    ): FileChunkData? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (!file.isFile) return@withContext null
                RandomAccessFile(file, "r").use { raf ->
                    val size = raf.length()
                    if (offset >= size) return@withContext FileChunkData(ByteArray(0), size)
                    val count = minOf(length.toLong(), size - offset).toInt()
                    val buffer = ByteArray(count)
                    raf.seek(offset)
                    raf.readFully(buffer)
                    FileChunkData(buffer, size)
                }
            } catch (e: IOException) {
                null
            } catch (e: SecurityException) {
                null
            }
        }

    private suspend fun directInfo(path: String): FileInfoData? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (!file.isFile) return@withContext null
                val digest = MessageDigest.getInstance("SHA-256")
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                FileInfoData(file.length(), digest.digest().joinToString("") { "%02x".format(it) })
            } catch (e: IOException) {
                null
            } catch (e: SecurityException) {
                null
            }
        }

    // ─── Staged root IO (any other absolute path) ───

    private fun newStageFile(): File {
        val dir =
            scratchDir
                ?: throw McpToolException.ActionFailed(
                    "File transfer staging is unavailable (no shell scratch directory)",
                )
        return File(dir, "xfer-${UUID.randomUUID()}.bin")
    }

    private suspend fun stagedWrite(
        path: String,
        offset: Long,
        bytes: ByteArray,
        truncate: Boolean,
        justification: String,
    ): Long {
        val stage = newStageFile()
        try {
            withContext(Dispatchers.IO) {
                stage.parentFile?.mkdirs()
                RandomAccessFile(stage, "rw").use { raf ->
                    raf.setLength(0)
                    raf.write(bytes)
                }
            }
            val quotedPath = ShellScriptBuilder.quoteForShell(path)
            val quotedStage = ShellScriptBuilder.quoteForShell(stage.absolutePath)
            val parent = path.substringBeforeLast('/', "").ifEmpty { "/" }
            val block = transferBlockSize(offset)
            val command =
                buildString {
                    append("mkdir -p ${ShellScriptBuilder.quoteForShell(parent)}")
                    if (truncate) {
                        append(" && : > $quotedPath")
                    }
                    append(" && dd if=$quotedStage of=$quotedPath bs=$block seek=${offset / block}")
                    append(" conv=notrunc 2>/dev/null")
                    append(" && stat -c %s $quotedPath")
                }
            val page = runner.run(command, justification)
            val output = page.chunkOutputOrFailure("Writing a chunk to '$path'")
            return output.trim().lines().lastOrNull()?.toLongOrNull()
                ?: throw McpToolException.ActionFailed(
                    "Could not write '$path': unexpected output: ${output.take(200)}",
                )
        } finally {
            stage.delete()
        }
    }

    private suspend fun stagedRead(
        path: String,
        offset: Long,
        length: Int,
        justification: String,
    ): FileChunkData {
        val stage = newStageFile()
        try {
            val quotedPath = ShellScriptBuilder.quoteForShell(path)
            val quotedStage = ShellScriptBuilder.quoteForShell(stage.absolutePath)
            val block = transferBlockSize(offset)
            val count = (length + block - 1) / block
            val command =
                "[ -f $quotedPath ] || exit 3; stat -c %s $quotedPath; " +
                    "dd if=$quotedPath of=$quotedStage bs=$block skip=${offset / block} " +
                    "count=$count 2>/dev/null"
            val page = runner.run(command, justification)
            if (page.snapshot.state == ShellProcessState.EXITED && page.snapshot.exitCode == 3) {
                throw McpToolException.ActionFailed(
                    "'$path' is not a regular file (or is not readable as root)",
                )
            }
            val output = page.chunkOutputOrFailure("Reading a chunk of '$path'")
            val fileSize =
                output.split('\n').firstOrNull()?.trim()?.toLongOrNull()
                    ?: throw McpToolException.ActionFailed(
                        "Could not read '$path': unexpected output: ${output.take(200)}",
                    )
            val staged =
                withContext(Dispatchers.IO) {
                    if (stage.isFile) stage.readBytes() else ByteArray(0)
                }
            val bytes = if (staged.size > length) staged.copyOf(length) else staged
            return FileChunkData(bytes, fileSize)
        } finally {
            stage.delete()
        }
    }

    private suspend fun rootInfo(
        path: String,
        justification: String,
    ): FileInfoData? {
        val quotedPath = ShellScriptBuilder.quoteForShell(path)
        val command =
            "if [ -f $quotedPath ]; then echo EXISTS; stat -c %s $quotedPath; sha256sum $quotedPath; " +
                "else echo MISSING; fi"
        val page = runner.run(command, justification)
        val output = page.chunkOutputOrFailure("Inspecting '$path'")
        val lines = output.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        return when (lines.firstOrNull()) {
            "MISSING" -> null
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
                FileInfoData(size, sha256)
            }
            else ->
                throw McpToolException.ActionFailed(
                    "Could not inspect '$path': unexpected output: ${output.take(200)}",
                )
        }
    }

    private companion object {
        val SHA256_HEX_PATTERN = Regex("^[0-9a-f]{64}$")
    }
}

/**
 * MCP tool: write_file_chunk
 *
 * Writes one base64 chunk of a binary file at an absolute path and byte offset. Stateless: each
 * call is independent, so callers upload a file of any size as a sequence of chunks (first call
 * with truncate=true), retry a failed chunk on its own, and verify the result with file_info.
 * The chunk bytes never travel on a shell command line — see [DeviceFileChunkStore].
 */
internal class WriteFileChunkHandler(
    private val store: FileChunkStore,
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

        val fileSize = store.writeChunk(path, offset, bytes, truncate, justification)

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
                "Write one chunk of a binary file at any absolute path. The chunk travels as " +
                    "'data_base64' (at most ${FileTransferLimits.MAX_CHUNK_BYTES} raw bytes per " +
                    "call) and is written at 'offset' without touching other bytes; the bytes are " +
                    "moved inside the server (directly, or staged for one short root `dd`), never " +
                    "on a shell command line. Paths the app cannot reach run as root through the " +
                    "same elevation authorization as an elevated shell command. Pass truncate=true " +
                    "on the first chunk to create/replace the file. Upload a whole file as a " +
                    "sequence of chunks, then check it with file_info. Returns JSON: path, offset, " +
                    "bytes_written, file_size.",
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
 * Reads one chunk of a binary file at an absolute path and byte offset and returns it
 * base64-encoded together with the total file size. 'length' is clamped to the per-call cap
 * server-side, so a caller asking for more still gets a bounded response; download a whole
 * file as a sequence of chunks until 'eof' is true, then verify it with file_info.
 */
internal class ReadFileChunkHandler(
    private val store: FileChunkStore,
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

        val data = store.readChunk(path, offset, length, justification)

        val resultJson =
            buildJsonObject {
                put("path", path)
                put("offset", offset)
                put("file_size", data.fileSize)
                put("bytes_returned", data.bytes.size)
                put("eof", offset + data.bytes.size >= data.fileSize)
                put("data_base64", Base64.getEncoder().encodeToString(data.bytes))
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
                "Read one chunk of a binary file at any absolute path. Returns JSON with the " +
                    "chunk as data_base64, the total file_size, bytes_returned and eof. 'length' " +
                    "defaults to ${FileTransferLimits.DEFAULT_CHUNK_BYTES} and is clamped to " +
                    "${FileTransferLimits.MAX_CHUNK_BYTES} raw bytes per call; the bytes are moved " +
                    "inside the server (directly, or staged for one short root `dd`), never on a " +
                    "shell command line. Paths the app cannot reach run as root through the same " +
                    "elevation authorization as an elevated shell command. Download a whole file " +
                    "as a sequence of chunks until eof, then verify it with file_info.",
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
 * Reports a file's existence, size and SHA-256 hash at any absolute path, in one call, so a
 * caller can verify a chunked upload or download end to end without extra round trips.
 */
internal class FileInfoHandler(
    private val store: FileChunkStore,
) {
    suspend fun execute(arguments: JsonObject?): CallToolResult {
        val path = requireAbsolutePath(arguments, "path")
        val justification = requireJustification(arguments)

        val info = store.fileInfo(path, justification)

        val resultJson =
            if (info == null) {
                buildJsonObject {
                    put("path", path)
                    put("exists", false)
                }
            } else {
                buildJsonObject {
                    put("path", path)
                    put("exists", true)
                    put("size", info.size)
                    put("sha256", info.sha256)
                }
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
                    "and SHA-256 hash, in a single call. Paths the app cannot reach are inspected " +
                    "as root through the same elevation authorization as an elevated shell " +
                    "command. Use it before and after chunked transfers (write_file_chunk / " +
                    "read_file_chunk) to verify integrity.",
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
}

fun registerFileTransferTools(
    registrar: LoggedToolRegistrar,
    context: ShellToolContext,
    toolNamePrefix: String,
    perms: ToolPermissionsConfig,
) {
    val store = DeviceFileChunkStore(context, context.scratchPath?.let { File(it) })
    if (perms.isToolEnabled(FileTransferToolNames.WRITE_FILE_CHUNK)) {
        WriteFileChunkHandler(store).register(registrar, toolNamePrefix)
    }
    if (perms.isToolEnabled(FileTransferToolNames.READ_FILE_CHUNK)) {
        ReadFileChunkHandler(store).register(registrar, toolNamePrefix)
    }
    if (perms.isToolEnabled(FileTransferToolNames.FILE_INFO)) {
        FileInfoHandler(store).register(registrar, toolNamePrefix)
    }
}

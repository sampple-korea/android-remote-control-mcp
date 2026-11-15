package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.data.model.ShellConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.SettingsRepository
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.services.shell.ElevationGrant
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellBackendKind
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellCommandAuthorizer
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellLaunch
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellOutputChunk
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellProcessManager
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellProcessPage
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellProcessSnapshot
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellProcessState
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import kotlin.time.TimeSource

@DisplayName("File transfer tools")
class FileTransferToolsTest {
    /** A store that records calls and replays programmed results, for handler-level tests. */
    private class FakeStore : FileChunkStore {
        var writeResult: Long = 0
        var readResult: FileChunkData = FileChunkData(ByteArray(0), 0)
        var infoResult: FileInfoData? = null
        var failure: Throwable? = null
        var writeCalls = 0
        var readCalls = 0
        var lastReadLength: Int? = null

        override suspend fun writeChunk(
            path: String,
            offset: Long,
            bytes: ByteArray,
            truncate: Boolean,
            justification: String,
        ): Long {
            writeCalls++
            failure?.let { throw it }
            return writeResult
        }

        override suspend fun readChunk(
            path: String,
            offset: Long,
            length: Int,
            justification: String,
        ): FileChunkData {
            readCalls++
            lastReadLength = length
            failure?.let { throw it }
            return readResult
        }

        override suspend fun fileInfo(
            path: String,
            justification: String,
        ): FileInfoData? {
            failure?.let { throw it }
            return infoResult
        }
    }

    private fun CallToolResult.text(): String = (content.single() as TextContent).text

    @Test
    fun `write_file_chunk reports the store's resulting file size`() =
        runTest {
            val store = FakeStore().apply { writeResult = 5 }

            val result =
                WriteFileChunkHandler(store).execute(
                    buildJsonObject {
                        put("path", "/data/local/tmp/blob.bin")
                        put("offset", 0)
                        put("data_base64", "aGVsbG8=") // "hello"
                        put("justification", "upload a test file")
                    },
                )

            val text = result.text()
            assertTrue(text.contains("\"bytes_written\":5"), text)
            assertTrue(text.contains("\"file_size\":5"), text)
            assertEquals(1, store.writeCalls)
        }

    @Test
    fun `write_file_chunk validates base64, size cap and offset before touching the store`() =
        runTest {
            val store = FakeStore()
            val handler = WriteFileChunkHandler(store)

            assertThrows<McpToolException.InvalidParams> {
                handler.execute(
                    buildJsonObject {
                        put("path", "/data/local/tmp/blob.bin")
                        put("data_base64", "not!!base64")
                        put("justification", "x")
                    },
                )
            }
            assertThrows<McpToolException.InvalidParams> {
                handler.execute(
                    buildJsonObject {
                        put("path", "/data/local/tmp/blob.bin")
                        put(
                            "data_base64",
                            Base64.getEncoder().encodeToString(ByteArray(FileTransferLimits.MAX_CHUNK_BYTES + 1)),
                        )
                        put("justification", "x")
                    },
                )
            }
            assertThrows<McpToolException.InvalidParams> {
                handler.execute(
                    buildJsonObject {
                        put("path", "/data/local/tmp/blob.bin")
                        put("offset", -1)
                        put("data_base64", "aGVsbG8=")
                        put("justification", "x")
                    },
                )
            }
            assertThrows<McpToolException.InvalidParams> {
                handler.execute(
                    buildJsonObject {
                        put("path", "relative/blob.bin")
                        put("data_base64", "aGVsbG8=")
                        put("justification", "x")
                    },
                )
            }
            assertEquals(0, store.writeCalls)
        }

    @Test
    fun `read_file_chunk returns the chunk with the total size and eof flag`() =
        runTest {
            val store =
                FakeStore().apply {
                    readResult = FileChunkData("hello".toByteArray(), 10)
                }

            val result =
                ReadFileChunkHandler(store).execute(
                    buildJsonObject {
                        put("path", "/data/local/tmp/blob.bin")
                        put("offset", 5)
                        put("length", 5)
                        put("justification", "download a test file")
                    },
                )

            val text = result.text()
            assertTrue(text.contains("\"file_size\":10"), text)
            assertTrue(text.contains("\"bytes_returned\":5"), text)
            assertTrue(text.contains("\"eof\":true"), text)
            assertTrue(text.contains("\"data_base64\":\"aGVsbG8=\""), text)
        }

    @Test
    fun `read_file_chunk clamps an oversized length to the per-call cap`() =
        runTest {
            val store = FakeStore()

            ReadFileChunkHandler(store).execute(
                buildJsonObject {
                    put("path", "/data/local/tmp/blob.bin")
                    put("length", FileTransferLimits.MAX_CHUNK_BYTES * 4)
                    put("justification", "download a test file")
                },
            )

            assertEquals(FileTransferLimits.MAX_CHUNK_BYTES, store.lastReadLength)
        }

    @Test
    fun `file_info returns size and sha256 for an existing file and reports a missing one`() =
        runTest {
            val sha = "a".repeat(64)
            val store = FakeStore().apply { infoResult = FileInfoData(42, sha) }

            val found =
                FileInfoHandler(store).execute(
                    buildJsonObject {
                        put("path", "/data/local/tmp/blob.bin")
                        put("justification", "verify an upload")
                    },
                )
            val text = found.text()
            assertTrue(text.contains("\"exists\":true"), text)
            assertTrue(text.contains("\"size\":42"), text)
            assertTrue(text.contains("\"sha256\":\"$sha\""), text)

            store.infoResult = null
            val missing =
                FileInfoHandler(store).execute(
                    buildJsonObject {
                        put("path", "/data/local/tmp/nope.bin")
                        put("justification", "check before an upload")
                    },
                )
            assertTrue(missing.text().contains("\"exists\":false"), missing.text())
        }

    @Test
    fun `handler propagates a store refusal`() =
        runTest {
            val store =
                FakeStore().apply {
                    failure = McpToolException.PermissionDenied("Refused: the command was not granted elevation.")
                }

            assertThrows<McpToolException.PermissionDenied> {
                WriteFileChunkHandler(store).execute(
                    buildJsonObject {
                        put("path", "/data/local/tmp/blob.bin")
                        put("data_base64", "aGVsbG8=")
                        put("justification", "upload")
                    },
                )
            }
        }

    @Test
    fun `transfer block size divides the offset and stays large`() {
        assertEquals(1_048_576L, transferBlockSize(0))
        assertEquals(1_048_576L, transferBlockSize(3L * 1_048_576))
        assertEquals(8_192L, transferBlockSize(73_728))
        assertEquals(8L, transferBlockSize(1_000))
        assertEquals(1L, transferBlockSize(7))
    }

    // ─── DeviceFileChunkStore: direct path on a real (temporary) file system ───

    private val processManager = mockk<ShellProcessManager>()
    private val authorizer = mockk<ShellCommandAuthorizer>()
    private val settingsRepository =
        mockk<SettingsRepository> {
            coEvery { getServerConfig() } returns ServerConfig(shellConfig = ShellConfig())
        }

    private fun deviceStore(
        timeSource: TimeSource,
        scratchDir: File,
    ): DeviceFileChunkStore {
        val context =
            ShellToolContext(processManager, authorizer, settingsRepository, scratchDir.absolutePath, timeSource)
        return DeviceFileChunkStore(context, scratchDir)
    }

    @Test
    fun `device store moves chunks directly when the path is reachable`(
        @TempDir tempDir: File,
    ) = runTest {
        val store = deviceStore(testScheduler.timeSource, File(tempDir, "scratch"))
        val target = File(tempDir, "blob.bin").absolutePath
        val payload = ByteArray(5_000) { (it % 251).toByte() }

        val sizeAfterFirst = store.writeChunk(target, 0, payload.copyOf(3_000), truncate = true, justification = "test")
        assertEquals(3_000L, sizeAfterFirst)
        val sizeAfterSecond =
            store.writeChunk(target, 3_000, payload.copyOfRange(3_000, 5_000), truncate = false, justification = "test")
        assertEquals(5_000L, sizeAfterSecond)

        val back = store.readChunk(target, 1_000, 2_000, justification = "test")
        assertEquals(5_000L, back.fileSize)
        assertArrayEquals(payload.copyOfRange(1_000, 3_000), back.bytes)

        val info = store.fileInfo(target, justification = "test")
        val expectedSha =
            MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        assertTrue(info != null)
        assertEquals(5_000L, info!!.size)
        assertEquals(expectedSha, info.sha256)

        // The direct path never asks for elevation.
        coVerify(exactly = 0) { authorizer.authorize(any(), any(), any()) }
    }

    // ─── DeviceFileChunkStore: staged root path through a mocked shell pipeline ───

    private fun page(
        output: String,
        exitCode: Int? = 0,
    ): ShellProcessPage =
        ShellProcessPage(
            ShellProcessSnapshot(
                id = 7,
                command = "cmd",
                backend = ShellBackendKind.APP,
                elevated = true,
                uid = 0,
                state = ShellProcessState.EXITED,
                exitCode = exitCode,
                failure = null,
                startedAtMs = 1,
                endedAtMs = 2,
                totalBytes = 3,
            ),
            ShellOutputChunk(output, 0, output.length.toLong(), 0, output.length.toLong()),
        )

    /** Stubs a granted authorization and a process returning [output]; returns the launch slot. */
    private fun stubGrantedRun(output: String): CapturingSlot<ShellLaunch> {
        val launch = slot<ShellLaunch>()
        coEvery { authorizer.authorize(any(), any(), any()) } returns ElevationGrant.AUTO_ALLOWED
        coEvery { processManager.startAndAwait(capture(launch), any(), any(), any()) } returns page(output)
        return launch
    }

    @Test
    fun `device store stages an unreachable write through one short dd command`(
        @TempDir tempDir: File,
    ) = runTest {
        val scratch = File(tempDir, "scratch")
        val launch = stubGrantedRun("5\n")
        val store = deviceStore(testScheduler.timeSource, scratch)

        val size =
            store.writeChunk(
                "/data/local/tmp/blob.bin",
                0,
                "hello".toByteArray(),
                truncate = true,
                justification = "upload a test file",
            )

        assertEquals(5L, size)
        val command = launch.captured.command
        assertTrue(command.contains("dd if='"), command)
        assertTrue(command.contains("of='/data/local/tmp/blob.bin'"), command)
        assertTrue(command.contains("conv=notrunc"), command)
        assertTrue(command.contains("&& : > '/data/local/tmp/blob.bin' &&"), command)
        // The chunk bytes themselves never appear on the command line.
        assertFalse(command.contains("aGVsbG8"), command)
        // The staging file is cleaned up.
        assertTrue(scratch.listFiles()?.none { it.name.startsWith("xfer-") } ?: true)
    }

    @Test
    fun `device store reads an unreachable file via a staged dd copy`(
        @TempDir tempDir: File,
    ) = runTest {
        val scratch = File(tempDir, "scratch")
        coEvery { authorizer.authorize(any(), any(), any()) } returns ElevationGrant.AUTO_ALLOWED
        coEvery { processManager.startAndAwait(any(), any(), any(), any()) } answers {
            // Simulate dd: materialize the stage file the command points at.
            val command = firstArg<ShellLaunch>().command
            val stagePath = Regex("""of='([^']+)'""").find(command)!!.groupValues[1]
            File(stagePath).writeBytes("hello".toByteArray())
            page("10\n")
        }
        val store = deviceStore(testScheduler.timeSource, scratch)

        val data = store.readChunk("/data/local/tmp/blob.bin", 5, 5, justification = "download a test file")

        assertEquals(10L, data.fileSize)
        assertArrayEquals("hello".toByteArray(), data.bytes)
        assertTrue(scratch.listFiles()?.none { it.name.startsWith("xfer-") } ?: true)
    }

    @Test
    fun `device store reports a non-regular file on the staged read path`(
        @TempDir tempDir: File,
    ) = runTest {
        val scratch = File(tempDir, "scratch")
        coEvery { authorizer.authorize(any(), any(), any()) } returns ElevationGrant.AUTO_ALLOWED
        coEvery { processManager.startAndAwait(any(), any(), any(), any()) } returns page("", exitCode = 3)
        val store = deviceStore(testScheduler.timeSource, scratch)

        val e =
            assertThrows<McpToolException.ActionFailed> {
                store.readChunk("/data/local/tmp", 0, 5, justification = "x")
            }
        assertTrue(e.message!!.contains("not a regular file"), e.message)
    }

    @Test
    fun `device store propagates an authorization denial on the staged path`(
        @TempDir tempDir: File,
    ) = runTest {
        val scratch = File(tempDir, "scratch")
        coEvery { authorizer.authorize(any(), any(), any()) } throws
            McpToolException.PermissionDenied("Refused: elevated commands are disabled in the app's Shell settings.")
        val store = deviceStore(testScheduler.timeSource, scratch)

        assertThrows<McpToolException.PermissionDenied> {
            store.writeChunk(
                "/data/local/tmp/blob.bin", 0, "hello".toByteArray(), truncate = true, justification = "upload",
            )
        }
        assertNull(scratch.listFiles()?.firstOrNull { it.name.startsWith("xfer-") })
        coVerify(exactly = 0) { processManager.startAndAwait(any(), any(), any(), any()) }
    }
}

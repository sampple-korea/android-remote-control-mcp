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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64
import kotlin.time.TimeSource

@DisplayName("File transfer tools")
class FileTransferToolsTest {
    private val processManager = mockk<ShellProcessManager>()
    private val authorizer = mockk<ShellCommandAuthorizer>()
    private val settingsRepository =
        mockk<SettingsRepository> {
            coEvery { getServerConfig() } returns ServerConfig(shellConfig = ShellConfig())
        }

    private fun runner(timeSource: TimeSource) =
        ElevatedCommandRunner(ShellToolContext(processManager, authorizer, settingsRepository, "/scratch", timeSource))

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

    private fun CallToolResult.text(): String = (content.single() as TextContent).text

    /** Stubs a granted authorization and a process returning [output]; returns the launch slot. */
    private fun stubGrantedRun(output: String): CapturingSlot<ShellLaunch> {
        val launch = slot<ShellLaunch>()
        coEvery { authorizer.authorize(any(), any(), any()) } returns ElevationGrant.AUTO_ALLOWED
        coEvery { processManager.startAndAwait(capture(launch), any(), any(), any()) } returns page(output)
        return launch
    }

    private fun stubDenied() {
        coEvery { authorizer.authorize(any(), any(), any()) } throws
            McpToolException.PermissionDenied("Refused: elevated commands are disabled in the app's Shell settings.")
    }

    @Test
    fun `write_file_chunk writes the decoded chunk at the offset and reports the new size`() =
        runTest {
            val launch = stubGrantedRun("5\n")

            val result =
                WriteFileChunkHandler(runner(testScheduler.timeSource)).execute(
                    buildJsonObject {
                        put("path", "/data/local/tmp/blob.bin")
                        put("offset", 0)
                        put("data_base64", "aGVsbG8=") // "hello"
                        put("justification", "upload a test file")
                    },
                )

            assertTrue(
                launch.captured.command.contains(
                    "mkdir -p '/data/local/tmp' && printf '%s' 'aGVsbG8=' | base64 -d " +
                        "| dd of='/data/local/tmp/blob.bin' bs=1 seek=0 conv=notrunc 2>/dev/null " +
                        "&& stat -c %s '/data/local/tmp/blob.bin'",
                ),
                launch.captured.command,
            )
            val text = result.text()
            assertTrue(text.contains("\"bytes_written\":5"), text)
            assertTrue(text.contains("\"file_size\":5"), text)
        }

    @Test
    fun `write_file_chunk truncates first when asked and writes at a later offset`() =
        runTest {
            val launch = stubGrantedRun("128\n")

            WriteFileChunkHandler(runner(testScheduler.timeSource)).execute(
                buildJsonObject {
                    put("path", "/sdcard/Download/blob.bin")
                    put("offset", 100)
                    put("data_base64", Base64.getEncoder().encodeToString(ByteArray(28)))
                    put("truncate", true)
                    put("justification", "restart an upload")
                },
            )

            assertTrue(launch.captured.command.contains("&& : > '/sdcard/Download/blob.bin' &&"), launch.captured.command)
            assertTrue(launch.captured.command.contains("seek=100"), launch.captured.command)
        }

    @Test
    fun `write_file_chunk validates base64, size cap and offset before running anything`() =
        runTest {
            val handler = WriteFileChunkHandler(runner(testScheduler.timeSource))

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
            coVerify(exactly = 0) { processManager.startAndAwait(any(), any(), any(), any()) }
        }

    @Test
    fun `read_file_chunk returns the chunk with the total size and eof flag`() =
        runTest {
            val launch = stubGrantedRun("10\naGVs\nbG8=\n") // size 10, wrapped base64 of "hello"

            val result =
                ReadFileChunkHandler(runner(testScheduler.timeSource)).execute(
                    buildJsonObject {
                        put("path", "/data/local/tmp/blob.bin")
                        put("offset", 5)
                        put("length", 5)
                        put("justification", "download a test file")
                    },
                )

            assertTrue(
                launch.captured.command.contains(
                    "dd if='/data/local/tmp/blob.bin' bs=1 skip=5 count=5 2>/dev/null | base64",
                ),
                launch.captured.command,
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
            val launch = stubGrantedRun("0\n")

            ReadFileChunkHandler(runner(testScheduler.timeSource)).execute(
                buildJsonObject {
                    put("path", "/data/local/tmp/blob.bin")
                    put("length", FileTransferLimits.MAX_CHUNK_BYTES * 4)
                    put("justification", "download a test file")
                },
            )

            assertTrue(
                launch.captured.command.contains("count=${FileTransferLimits.MAX_CHUNK_BYTES}"),
                launch.captured.command,
            )
        }

    @Test
    fun `read_file_chunk reports a non-regular file`() =
        runTest {
            val handler = ReadFileChunkHandler(runner(testScheduler.timeSource))
            coEvery { authorizer.authorize(any(), any(), any()) } returns ElevationGrant.AUTO_ALLOWED
            coEvery { processManager.startAndAwait(any(), any(), any(), any()) } returns page("", exitCode = 3)

            val e =
                assertThrows<McpToolException.ActionFailed> {
                    handler.execute(
                        buildJsonObject {
                            put("path", "/data/local/tmp")
                            put("justification", "x")
                        },
                    )
                }
            assertTrue(e.message!!.contains("not a regular file"), e.message)
        }

    @Test
    fun `file_info returns size and sha256 for an existing file`() =
        runTest {
            val sha = "a".repeat(64)
            stubGrantedRun("EXISTS\n42\n$sha  /data/local/tmp/blob.bin\n")

            val result =
                FileInfoHandler(runner(testScheduler.timeSource)).execute(
                    buildJsonObject {
                        put("path", "/data/local/tmp/blob.bin")
                        put("justification", "verify an upload")
                    },
                )

            val text = result.text()
            assertTrue(text.contains("\"exists\":true"), text)
            assertTrue(text.contains("\"size\":42"), text)
            assertTrue(text.contains("\"sha256\":\"$sha\""), text)
        }

    @Test
    fun `file_info reports a missing file without failing`() =
        runTest {
            stubGrantedRun("MISSING\n")

            val result =
                FileInfoHandler(runner(testScheduler.timeSource)).execute(
                    buildJsonObject {
                        put("path", "/data/local/tmp/nope.bin")
                        put("justification", "check before an upload")
                    },
                )

            assertTrue(result.text().contains("\"exists\":false"), result.text())
        }

    @Test
    fun `file transfer tools are refused when authorization is denied`() =
        runTest {
            stubDenied()

            assertThrows<McpToolException.PermissionDenied> {
                WriteFileChunkHandler(runner(testScheduler.timeSource)).execute(
                    buildJsonObject {
                        put("path", "/data/local/tmp/blob.bin")
                        put("data_base64", "aGVsbG8=")
                        put("justification", "upload")
                    },
                )
            }
            assertThrows<McpToolException.PermissionDenied> {
                FileInfoHandler(runner(testScheduler.timeSource)).execute(
                    buildJsonObject {
                        put("path", "/data/local/tmp/blob.bin")
                        put("justification", "verify")
                    },
                )
            }
            coVerify(exactly = 0) { processManager.startAndAwait(any(), any(), any(), any()) }
        }
}

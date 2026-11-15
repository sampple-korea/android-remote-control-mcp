package com.danielealbano.androidremotecontrolmcp.mcp.tools

import android.content.Context
import android.content.res.AssetManager
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
import io.mockk.every
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.time.TimeSource

@DisplayName("type_virtual_keyboard tool")
class TypeVirtualKeyboardToolTest {
    @TempDir
    lateinit var filesDir: File

    private val processManager = mockk<ShellProcessManager>()
    private val authorizer = mockk<ShellCommandAuthorizer>()
    private val settingsRepository =
        mockk<SettingsRepository> {
            coEvery { getServerConfig() } returns ServerConfig(shellConfig = ShellConfig())
        }

    private lateinit var appContext: Context

    @BeforeEach
    fun setUp() {
        val dir = filesDir
        val assetManager =
            mockk<AssetManager> {
                every { open("vkbd") } answers { ByteArrayInputStream(ASSET_BYTES) }
            }
        appContext =
            mockk {
                every { filesDir } returns dir
                every { assets } returns assetManager
            }
    }

    private fun handler(timeSource: TimeSource): TypeVirtualKeyboardHandler {
        val context = ShellToolContext(processManager, authorizer, settingsRepository, "/scratch", timeSource)
        return TypeVirtualKeyboardHandler(appContext, ElevatedCommandRunner(context))
    }

    private fun page(
        output: String,
        exitCode: Int? = 0,
    ): ShellProcessPage {
        val snapshot =
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
            )
        val chunk = ShellOutputChunk(output, 0, output.length.toLong(), 0, output.length.toLong())
        return ShellProcessPage(snapshot, chunk)
    }

    /** Stubs a granted authorization and a helper process exiting with [exitCode]; returns the launch slot. */
    private fun stubGrantedRun(
        output: String = "",
        exitCode: Int? = 0,
    ): CapturingSlot<ShellLaunch> {
        val launch = slot<ShellLaunch>()
        coEvery { authorizer.authorize(any(), any(), any()) } returns ElevationGrant.AUTO_ALLOWED
        coEvery { processManager.startAndAwait(capture(launch), any(), any(), any()) } returns page(output, exitCode)
        return launch
    }

    private fun CallToolResult.text(): String = (content.single() as TextContent).text

    private fun args(text: String) =
        buildJsonObject {
            put("text", text)
            put("justification", "type into the sign-in field")
        }

    @Test
    fun `types the text from the stdin file and never puts it on the command line`() =
        runTest {
            val launch = stubGrantedRun()

            val result = handler(testScheduler.timeSource).execute(args("hello"))

            assertEquals("Typed 5 characters with the virtual keyboard.", result.text())
            val binary = File(filesDir, "vkbd")
            val input = File(filesDir, "vkbd-input.txt")
            assertEquals("'${binary.absolutePath}' - < '${input.absolutePath}'", launch.captured.command)
            assertFalse(launch.captured.command.contains("hello"))
            assertEquals("hello", input.readText())
            assertArrayEquals(ASSET_BYTES, binary.readBytes())
            assertTrue(binary.canExecute())
        }

    @Test
    fun `a stale extracted helper is replaced with the bundled one`() =
        runTest {
            stubGrantedRun()
            val binary = File(filesDir, "vkbd")
            binary.writeBytes(byteArrayOf(1, 2, 3))

            handler(testScheduler.timeSource).execute(args("x"))

            assertArrayEquals(ASSET_BYTES, binary.readBytes())
        }

    @Test
    fun `rejects empty text and runs nothing`() =
        runTest {
            assertThrows<McpToolException.InvalidParams> {
                handler(testScheduler.timeSource).execute(args(""))
            }
            coVerify(exactly = 0) { processManager.startAndAwait(any(), any(), any(), any()) }
        }

    @Test
    fun `rejects text over the length limit`() =
        runTest {
            assertThrows<McpToolException.InvalidParams> {
                handler(testScheduler.timeSource).execute(args("a".repeat(501)))
            }
            coVerify(exactly = 0) { processManager.startAndAwait(any(), any(), any(), any()) }
        }

    @Test
    fun `rejects characters the keyboard cannot type`() =
        runTest {
            assertThrows<McpToolException.InvalidParams> {
                handler(testScheduler.timeSource).execute(args("비밀번호"))
            }
            coVerify(exactly = 0) { processManager.startAndAwait(any(), any(), any(), any()) }
        }

    @Test
    fun `rejects a missing justification`() =
        runTest {
            assertThrows<McpToolException.InvalidParams> {
                handler(testScheduler.timeSource).execute(buildJsonObject { put("text", "hello") })
            }
            coVerify(exactly = 0) { processManager.startAndAwait(any(), any(), any(), any()) }
        }

    @Test
    fun `a helper failure surfaces its output as an action failure`() =
        runTest {
            stubGrantedRun(output = "open /dev/uinput: Permission denied\n", exitCode = 1)

            val error =
                assertThrows<McpToolException.ActionFailed> {
                    handler(testScheduler.timeSource).execute(args("hello"))
                }

            assertTrue(error.message!!.contains("Permission denied"), error.message)
        }

    private companion object {
        val ASSET_BYTES = byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 0x02, 0x01, 0x01, 0x00)
    }
}

package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.data.model.ServerLogEntry
import com.danielealbano.androidremotecontrolmcp.data.model.ShellConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.SettingsRepository
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.services.shell.ElevationGrant
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellAuthorizationRequest
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellBackendKind
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellCommandAuthorizer
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellLaunch
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellOutputChunk
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellProcessManager
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellProcessPage
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellProcessSnapshot
import com.danielealbano.androidremotecontrolmcp.services.shell.ShellProcessState
import com.danielealbano.androidremotecontrolmcp.testutil.RecordingServerLogRepository
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.TimeSource

@DisplayName("Root tools")
class RootToolsTest {
    private val processManager = mockk<ShellProcessManager>()
    private val authorizer = mockk<ShellCommandAuthorizer>()
    private val settingsRepository =
        mockk<SettingsRepository> {
            coEvery { getServerConfig() } returns ServerConfig(shellConfig = ShellConfig())
        }
    private val serverLog = RecordingServerLogRepository()

    private fun context(timeSource: TimeSource): ShellToolContext =
        ShellToolContext(processManager, authorizer, settingsRepository, "/scratch", timeSource)

    private fun runner(timeSource: TimeSource) = ElevatedCommandRunner(context(timeSource))

    private fun snapshot(
        state: ShellProcessState = ShellProcessState.EXITED,
        exitCode: Int? = 0,
        failure: String? = null,
    ): ShellProcessSnapshot =
        ShellProcessSnapshot(
            id = 7,
            command = "cmd",
            backend = ShellBackendKind.APP,
            elevated = true,
            uid = 0,
            state = state,
            exitCode = exitCode,
            failure = failure,
            startedAtMs = 1,
            endedAtMs = 2,
            totalBytes = 3,
        )

    private fun page(
        output: String,
        state: ShellProcessState = ShellProcessState.EXITED,
        exitCode: Int? = 0,
        failure: String? = null,
    ): ShellProcessPage =
        ShellProcessPage(
            snapshot(state, exitCode, failure),
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

    /** Stubs the authorizer refusing every command, as it does when elevation is disabled. */
    private fun stubDenied() {
        coEvery { authorizer.authorize(any(), any(), any()) } throws
            McpToolException.PermissionDenied("Refused: elevated commands are disabled in the app's Shell settings.")
    }

    @Test
    fun `runner authorizes as elevated with the justification and runs with the grant`() =
        runTest {
            val request = slot<ShellAuthorizationRequest>()
            val launch = slot<ShellLaunch>()
            coEvery { authorizer.authorize(capture(request), any(), any()) } returns ElevationGrant.APPROVED
            coEvery { processManager.startAndAwait(capture(launch), any(), any(), any()) } returns page("done\n")

            runner(testScheduler.timeSource).run("pm install -r '/x.apk'", "install the update")

            assertTrue(request.captured.elevated)
            assertEquals("pm install -r '/x.apk'", request.captured.command)
            assertEquals("install the update", request.captured.justification)
            assertEquals(ElevationGrant.APPROVED, launch.captured.elevation?.grant)
            assertEquals("install the update", launch.captured.elevation?.justification)
            assertEquals("/", launch.captured.workdir)
        }

    @Test
    fun `runner propagates an authorization refusal and runs nothing`() =
        runTest {
            stubDenied()

            assertThrows<McpToolException.PermissionDenied> {
                runner(testScheduler.timeSource).run("reboot", "restart the device")
            }
            coVerify(exactly = 0) { processManager.startAndAwait(any(), any(), any(), any()) }
        }

    @Test
    fun `runner refuses when the authorizer grants no elevation`() =
        runTest {
            coEvery { authorizer.authorize(any(), any(), any()) } returns null

            assertThrows<McpToolException.PermissionDenied> {
                runner(testScheduler.timeSource).run("reboot", "restart the device")
            }
            coVerify(exactly = 0) { processManager.startAndAwait(any(), any(), any(), any()) }
        }

    @Test
    fun `install_apk installs with pm install -r and reports success`() =
        runTest {
            val launch = stubGrantedRun("Success\n")

            val result =
                InstallApkHandler(runner(testScheduler.timeSource)).execute(
                    buildJsonObject {
                        put("apk_path", "/sdcard/Download/app.apk")
                        put("justification", "install the update")
                    },
                )

            assertEquals("pm install -r '/sdcard/Download/app.apk'", launch.captured.command)
            assertTrue(result.text().contains("Installed '/sdcard/Download/app.apk' successfully"), result.text())
        }

    @Test
    fun `install_apk carries the pm failure output as a tool error`() =
        runTest {
            stubGrantedRun("Failure [INSTALL_FAILED_OLDER_SDK]\n")

            val e =
                assertThrows<McpToolException.ActionFailed> {
                    InstallApkHandler(runner(testScheduler.timeSource)).execute(
                        buildJsonObject {
                            put("apk_path", "/sdcard/Download/app.apk")
                            put("justification", "install the update")
                        },
                    )
                }
            assertTrue(e.message!!.contains("INSTALL_FAILED_OLDER_SDK"), e.message)
        }

    @Test
    fun `install_apk validates its arguments before authorizing`() =
        runTest {
            val handler = InstallApkHandler(runner(testScheduler.timeSource))
            assertThrows<McpToolException.InvalidParams> {
                handler.execute(
                    buildJsonObject {
                        put("apk_path", "relative/app.apk")
                        put("justification", "x")
                    },
                )
            }
            assertThrows<McpToolException.InvalidParams> {
                handler.execute(
                    buildJsonObject {
                        put("apk_path", "/sdcard/app.zip")
                        put("justification", "x")
                    },
                )
            }
            assertThrows<McpToolException.InvalidParams> {
                handler.execute(buildJsonObject { put("apk_path", "/sdcard/app.apk") })
            }
            coVerify(exactly = 0) { authorizer.authorize(any(), any(), any()) }
        }

    @Test
    fun `install_apk is refused when authorization is denied`() =
        runTest {
            stubDenied()

            assertThrows<McpToolException.PermissionDenied> {
                InstallApkHandler(runner(testScheduler.timeSource)).execute(
                    buildJsonObject {
                        put("apk_path", "/sdcard/app.apk")
                        put("justification", "install the update")
                    },
                )
            }
            coVerify(exactly = 0) { processManager.startAndAwait(any(), any(), any(), any()) }
        }

    @Test
    fun `uninstall_app uninstalls with pm uninstall and reports success`() =
        runTest {
            val launch = stubGrantedRun("Success\n")

            val result =
                UninstallAppHandler(runner(testScheduler.timeSource)).execute(
                    buildJsonObject {
                        put("package_id", "com.example.app")
                        put("justification", "remove the broken build")
                    },
                )

            assertEquals("pm uninstall 'com.example.app'", launch.captured.command)
            assertTrue(result.text().contains("Uninstalled 'com.example.app' successfully"), result.text())
        }

    @Test
    fun `uninstall_app validates the package id and carries pm failures`() =
        runTest {
            val handler = UninstallAppHandler(runner(testScheduler.timeSource))
            assertThrows<McpToolException.InvalidParams> {
                handler.execute(
                    buildJsonObject {
                        put("package_id", "bad package; rm -rf /")
                        put("justification", "x")
                    },
                )
            }
            stubGrantedRun("Failure [DELETE_FAILED_INTERNAL_ERROR]\n")
            val e =
                assertThrows<McpToolException.ActionFailed> {
                    handler.execute(
                        buildJsonObject {
                            put("package_id", "com.example.app")
                            put("justification", "remove the broken build")
                        },
                    )
                }
            assertTrue(e.message!!.contains("DELETE_FAILED_INTERNAL_ERROR"), e.message)
        }

    @Test
    fun `uninstall_app is refused when authorization is denied`() =
        runTest {
            stubDenied()

            assertThrows<McpToolException.PermissionDenied> {
                UninstallAppHandler(runner(testScheduler.timeSource)).execute(
                    buildJsonObject {
                        put("package_id", "com.example.app")
                        put("justification", "remove the broken build")
                    },
                )
            }
            coVerify(exactly = 0) { processManager.startAndAwait(any(), any(), any(), any()) }
        }

    @Test
    fun `reboot_device writes the server log before running the reboot command`() =
        runTest {
            val launch = slot<ShellLaunch>()
            var logWrittenBeforeRun = false
            coEvery { authorizer.authorize(any(), any(), any()) } returns ElevationGrant.AUTO_ALLOWED
            coEvery { processManager.startAndAwait(capture(launch), any(), any(), any()) } answers {
                logWrittenBeforeRun = serverLog.entries.any { it.type == ServerLogEntry.Type.SHELL }
                page("", state = ShellProcessState.KILLED, exitCode = null)
            }

            val result =
                RebootDeviceHandler(runner(testScheduler.timeSource), serverLog).execute(
                    buildJsonObject { put("justification", "apply the system update") },
                )

            assertEquals("reboot", launch.captured.command)
            assertTrue(logWrittenBeforeRun)
            val entry = serverLog.ofType(ServerLogEntry.Type.SHELL).single()
            assertTrue(entry.message.contains("reboot_device"), entry.message)
            assertTrue(entry.message.contains("apply the system update"), entry.message)
            assertTrue(result.text().contains("Reboot command authorized and started"), result.text())
        }

    @Test
    fun `reboot_device fails when the command cannot start and is refused when denied`() =
        runTest {
            val handler = RebootDeviceHandler(runner(testScheduler.timeSource), serverLog)
            coEvery { authorizer.authorize(any(), any(), any()) } returns ElevationGrant.AUTO_ALLOWED
            coEvery { processManager.startAndAwait(any(), any(), any(), any()) } returns
                page("", state = ShellProcessState.FAILED, exitCode = null, failure = "su not available")
            assertThrows<McpToolException.ActionFailed> {
                handler.execute(buildJsonObject { put("justification", "restart") })
            }

            stubDenied()
            assertThrows<McpToolException.PermissionDenied> {
                handler.execute(buildJsonObject { put("justification", "restart") })
            }
        }

    @Test
    fun `read_logcat reads the last lines and applies a fixed-string filter`() =
        runTest {
            val launch = stubGrantedRun("I/MyTag: hello\nW/Other: bye\n")

            val handler = ReadLogcatHandler(runner(testScheduler.timeSource))
            val all =
                handler.execute(buildJsonObject { put("justification", "diagnose the crash") })
            assertEquals("logcat -d -t 200", launch.captured.command)
            assertTrue(all.text().contains("Last 200 log lines:"), all.text())
            assertTrue(all.text().contains("I/MyTag: hello"), all.text())

            val filtered =
                handler.execute(
                    buildJsonObject {
                        put("lines", 50)
                        put("filter", "MyTag")
                        put("justification", "diagnose the crash")
                    },
                )
            assertEquals("logcat -d -t 50 | grep -F -e 'MyTag'", launch.captured.command)
            assertTrue(filtered.text().contains("containing 'MyTag'"), filtered.text())
        }

    @Test
    fun `read_logcat quotes the filter and validates its arguments`() =
        runTest {
            val launch = stubGrantedRun("")
            val handler = ReadLogcatHandler(runner(testScheduler.timeSource))

            val empty =
                handler.execute(
                    buildJsonObject {
                        put("filter", "it's")
                        put("justification", "diagnose")
                    },
                )
            assertEquals("logcat -d -t 200 | grep -F -e 'it'\\''s'", launch.captured.command)
            assertTrue(empty.text().contains("(no log lines)"), empty.text())

            assertThrows<McpToolException.InvalidParams> {
                handler.execute(
                    buildJsonObject {
                        put("lines", 0)
                        put("justification", "x")
                    },
                )
            }
            assertThrows<McpToolException.InvalidParams> {
                handler.execute(
                    buildJsonObject {
                        put("lines", 5001)
                        put("justification", "x")
                    },
                )
            }
        }

    @Test
    fun `read_logcat is refused when authorization is denied`() =
        runTest {
            stubDenied()

            assertThrows<McpToolException.PermissionDenied> {
                ReadLogcatHandler(runner(testScheduler.timeSource)).execute(
                    buildJsonObject { put("justification", "diagnose") },
                )
            }
            coVerify(exactly = 0) { processManager.startAndAwait(any(), any(), any(), any()) }
        }

    @Test
    fun `read_root_file returns numbered lines for the requested window`() =
        runTest {
            val launch = stubGrantedRun("10\nl3\nl4\n")

            val result =
                ReadRootFileHandler(runner(testScheduler.timeSource)).execute(
                    buildJsonObject {
                        put("path", "/data/data/com.example/shared_prefs/prefs.xml")
                        put("offset", 3)
                        put("limit", 2)
                        put("justification", "inspect the app's stored settings")
                    },
                )

            assertEquals(
                "[ -f '/data/data/com.example/shared_prefs/prefs.xml' ] || exit 3; " +
                    "wc -l < '/data/data/com.example/shared_prefs/prefs.xml'; " +
                    "sed -n '3,4p' '/data/data/com.example/shared_prefs/prefs.xml'",
                launch.captured.command,
            )
            val text = result.text()
            assertTrue(text.contains("3| l3"), text)
            assertTrue(text.contains("4| l4"), text)
            assertTrue(text.contains("Use offset=5 to continue reading. Total lines: 10"), text)
        }

    @Test
    fun `read_root_file reads a whole small file without a continuation footer`() =
        runTest {
            stubGrantedRun("2\nfirst\nsecond\n")

            val result =
                ReadRootFileHandler(runner(testScheduler.timeSource)).execute(
                    buildJsonObject {
                        put("path", "/system/build.prop")
                        put("justification", "check the build fingerprint")
                    },
                )

            val text = result.text()
            assertTrue(text.contains("1| first"), text)
            assertTrue(text.contains("2| second"), text)
            assertTrue(!text.contains("More lines available"), text)
        }

    @Test
    fun `read_root_file reports a non-regular file and validates its arguments`() =
        runTest {
            val handler = ReadRootFileHandler(runner(testScheduler.timeSource))
            coEvery { authorizer.authorize(any(), any(), any()) } returns ElevationGrant.AUTO_ALLOWED
            coEvery { processManager.startAndAwait(any(), any(), any(), any()) } returns page("", exitCode = 3)

            val e =
                assertThrows<McpToolException.ActionFailed> {
                    handler.execute(
                        buildJsonObject {
                            put("path", "/data/data")
                            put("justification", "x")
                        },
                    )
                }
            assertTrue(e.message!!.contains("not a regular file"), e.message)

            assertThrows<McpToolException.InvalidParams> {
                handler.execute(
                    buildJsonObject {
                        put("path", "relative/file.txt")
                        put("justification", "x")
                    },
                )
            }
            assertThrows<McpToolException.InvalidParams> {
                handler.execute(
                    buildJsonObject {
                        put("path", "/data/file.txt")
                        put("offset", 0)
                        put("justification", "x")
                    },
                )
            }
            assertThrows<McpToolException.InvalidParams> {
                handler.execute(
                    buildJsonObject {
                        put("path", "/data/file.txt")
                        put("limit", 201)
                        put("justification", "x")
                    },
                )
            }
        }

    @Test
    fun `read_root_file is refused when authorization is denied`() =
        runTest {
            stubDenied()

            assertThrows<McpToolException.PermissionDenied> {
                ReadRootFileHandler(runner(testScheduler.timeSource)).execute(
                    buildJsonObject {
                        put("path", "/data/file.txt")
                        put("justification", "inspect")
                    },
                )
            }
            coVerify(exactly = 0) { processManager.startAndAwait(any(), any(), any(), any()) }
        }
}

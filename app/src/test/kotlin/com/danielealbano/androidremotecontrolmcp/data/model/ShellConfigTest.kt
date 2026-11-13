package com.danielealbano.androidremotecontrolmcp.data.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ShellConfig")
class ShellConfigTest {
    @Test
    fun `output defaults let a call return one mebibyte and a process keep eight`() {
        val config = ShellConfig()

        assertEquals(1024, config.limit(ShellLimit.OUTPUT_PER_CALL_KIB))
        assertEquals(8192, config.limit(ShellLimit.OUTPUT_BUFFER_KIB))
    }

    @Test
    fun `the default limits fit the output memory cap`() {
        val config = ShellConfig()

        SHELL_OUTPUT_MEMORY_LIMITS.forEach { limit ->
            assertTrue(
                config.outputMemoryKib(limit, config.limit(limit)) <= SHELL_OUTPUT_MEMORY_CAP_KIB,
                "${limit.key} at its default exceeds the memory cap",
            )
        }
    }

    @Test
    fun `every default is inside its valid range`() {
        val config = ShellConfig()

        ShellLimit.entries.forEach { limit ->
            assertTrue(limit.isValid(config.limit(limit)), "${limit.key} default out of range")
        }
    }
}

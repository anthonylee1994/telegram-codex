package com.telegram.codex.integration.codex

import com.fasterxml.jackson.databind.ObjectMapper
import com.telegram.codex.integration.codex.schema.CodexOutputSchema
import com.telegram.codex.shared.config.AppProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ExecRunnerTest {
    @Test
    fun runUsesTempDirectoryAsWorkingDirectory() {
        val execRunner = CapturingExecRunner(properties(), ObjectMapper())

        val reply = execRunner.run("prompt", emptyList(), TestOutputSchema("object"))

        assertEquals("{\"text\":\"ok\",\"suggested_replies\":[\"a\",\"b\",\"c\"]}", reply)
        assertEquals(execRunner.outputPath!!.parent, execRunner.workingDirectory)
        assertNotEquals(Path.of(".").toAbsolutePath().normalize(), execRunner.workingDirectory!!.toAbsolutePath().normalize())
    }

    @Test
    fun runWrapsSystemPromptAndUserPromptAsSeparateSections() {
        val execRunner = CapturingExecRunner(properties(), ObjectMapper())

        execRunner.run("system rules", "user payload", emptyList(), TestOutputSchema("object"))

        assertTrue(execRunner.prompt!!.contains("<system_prompt>"))
        assertTrue(execRunner.prompt!!.contains("system rules"))
        assertTrue(execRunner.prompt!!.contains("</system_prompt>"))
        assertTrue(execRunner.prompt!!.contains("<user_prompt>"))
        assertTrue(execRunner.prompt!!.contains("user payload"))
        assertTrue(execRunner.prompt!!.contains("</user_prompt>"))
    }

    private class CapturingExecRunner(properties: AppProperties, objectMapper: ObjectMapper) : ExecRunner(properties, objectMapper) {
        var outputPath: Path? = null
        var workingDirectory: Path? = null
        var prompt: String? = null

        override fun executeCommand(command: List<String>, workingDirectory: Path?, prompt: String?, timeoutSeconds: Long): ProcessExecutor.ProcessResult {
            this.workingDirectory = workingDirectory
            outputPath = Path.of(command[command.indexOf("--output-last-message") + 1])
            this.prompt = prompt
            Files.writeString(outputPath, "{\"text\":\"ok\",\"suggested_replies\":[\"a\",\"b\",\"c\"]}")
            return ProcessExecutor.ProcessResult(0, "", "", false)
        }
    }

    private data class TestOutputSchema(val type: String) : CodexOutputSchema

    companion object {
        private fun properties(): AppProperties = AppProperties().apply {
            baseUrl = "https://example.com"
            telegramBotToken = "token"
            telegramWebhookSecret = "secret"
        }
    }
}

package com.telegram.codex.integration.codex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.integration.codex.schema.CodexOutputSchema;
import com.telegram.codex.shared.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecRunnerTest {

    private static final CodexOutputSchema TEST_SCHEMA = new TestOutputSchema("object");

    @Test
    void runUsesTempDirectoryAsWorkingDirectory() {
        AppProperties properties = new AppProperties();
        properties.setBaseUrl("https://example.com");
        properties.setTelegramBotToken("token");
        properties.setTelegramWebhookSecret("secret");
        CapturingExecRunner execRunner = new CapturingExecRunner(properties, new ObjectMapper());

        String reply = execRunner.run("prompt", List.of(), TEST_SCHEMA);

        assertEquals("{\"text\":\"ok\",\"suggested_replies\":[\"a\",\"b\",\"c\"]}", reply);
        assertEquals(execRunner.outputPath.getParent(), execRunner.workingDirectory);
        assertNotEquals(Path.of(".").toAbsolutePath().normalize(), execRunner.workingDirectory.toAbsolutePath().normalize());
    }

    @Test
    void runWrapsSystemPromptAndUserPromptAsSeparateSections() {
        AppProperties properties = new AppProperties();
        properties.setBaseUrl("https://example.com");
        properties.setTelegramBotToken("token");
        properties.setTelegramWebhookSecret("secret");
        CapturingExecRunner execRunner = new CapturingExecRunner(properties, new ObjectMapper());

        execRunner.run("system rules", "user payload", List.of(), TEST_SCHEMA);

        assertTrue(execRunner.prompt.contains("<system_prompt>"));
        assertTrue(execRunner.prompt.contains("system rules"));
        assertTrue(execRunner.prompt.contains("</system_prompt>"));
        assertTrue(execRunner.prompt.contains("<user_prompt>"));
        assertTrue(execRunner.prompt.contains("user payload"));
        assertTrue(execRunner.prompt.contains("</user_prompt>"));
    }

    private static class CapturingExecRunner extends ExecRunner {
        private Path outputPath;
        private Path workingDirectory;
        private String prompt;

        private CapturingExecRunner(AppProperties properties, ObjectMapper objectMapper) {
            super(properties, objectMapper);
        }

        @Override
        ProcessExecutor.ProcessResult executeCommand(List<String> command, Path workingDirectory, String prompt, long timeoutSeconds)
            throws java.io.IOException {
            this.workingDirectory = workingDirectory;
            this.outputPath = Path.of(command.get(command.indexOf("--output-last-message") + 1));
            this.prompt = prompt;
            Files.writeString(outputPath, "{\"text\":\"ok\",\"suggested_replies\":[\"a\",\"b\",\"c\"]}");
            return new ProcessExecutor.ProcessResult(0, "", "", false);
        }
    }

    private record TestOutputSchema(String type) implements CodexOutputSchema {
    }
}

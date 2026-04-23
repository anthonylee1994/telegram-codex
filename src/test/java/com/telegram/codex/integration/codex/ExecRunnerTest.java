package com.telegram.codex.integration.codex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.shared.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ExecRunnerTest {

    @Test
    void runUsesTempDirectoryAsWorkingDirectory() throws Exception {
        AppProperties properties = new AppProperties();
        properties.setBaseUrl("https://example.com");
        properties.setTelegramBotToken("token");
        properties.setTelegramWebhookSecret("secret");
        CapturingExecRunner execRunner = new CapturingExecRunner(properties, new ObjectMapper());

        String reply = execRunner.run("prompt", List.of(), Map.of("type", "object"));

        assertEquals("{\"text\":\"ok\",\"suggested_replies\":[\"a\",\"b\",\"c\"]}", reply);
        assertEquals(execRunner.outputPath.getParent(), execRunner.workingDirectory);
        assertNotEquals(Path.of(".").toAbsolutePath().normalize(), execRunner.workingDirectory.toAbsolutePath().normalize());
    }

    private static class CapturingExecRunner extends ExecRunner {
        private Path outputPath;
        private Path workingDirectory;

        private CapturingExecRunner(AppProperties properties, ObjectMapper objectMapper) {
            super(properties, objectMapper);
        }

        @Override
        ProcessExecutor.ProcessResult executeCommand(List<String> command, Path workingDirectory, String prompt, long timeoutSeconds)
            throws java.io.IOException {
            this.workingDirectory = workingDirectory;
            this.outputPath = Path.of(command.get(command.indexOf("--output-last-message") + 1));
            Files.writeString(outputPath, "{\"text\":\"ok\",\"suggested_replies\":[\"a\",\"b\",\"c\"]}");
            return new ProcessExecutor.ProcessResult(0, "", "", false);
        }
    }
}

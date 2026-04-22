package com.telegram.codex.codex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.config.AppProperties;
import com.telegram.codex.constants.CodexConstants;
import com.telegram.codex.exception.ExecutionException;
import com.telegram.codex.exception.ExecutionTimeoutException;
import com.telegram.codex.util.ProcessExecutor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ExecRunner {

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public ExecRunner(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String run(String prompt, List<Path> imageFilePaths, Map<String, Object> outputSchema) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("telegram-codex-");
            Path outputPath = tempDir.resolve("reply.txt");
            Path schemaPath = outputSchema == null ? null : tempDir.resolve("reply-schema.json");
            if (schemaPath != null) {
                Files.writeString(schemaPath, objectMapper.writeValueAsString(outputSchema), StandardCharsets.UTF_8);
            }

            List<String> command = buildCommand(outputPath, schemaPath, imageFilePaths);

            ProcessExecutor.ProcessResult result = executeCommand(
                command,
                tempDir,
                prompt,
                properties.getCodexExecTimeoutSeconds()
            );

            if (result.timedOut()) {
                throw new ExecutionTimeoutException("codex exec timed out after " + properties.getCodexExecTimeoutSeconds() + " seconds");
            }
            if (result.exitCode() != 0) {
                String errorMessage = result.stderr().isBlank() ? "unknown error" : result.stderr().trim();
                throw new ExecutionException("codex exec failed: " + errorMessage);
            }
            String replyText = Files.readString(outputPath, StandardCharsets.UTF_8).trim();
            if (replyText.isEmpty()) {
                throw new ExecutionException("codex exec returned an empty reply");
            }
            return replyText;
        } catch (ExecutionTimeoutException error) {
            throw error;
        } catch (ExecutionException error) {
            throw error;
        } catch (IOException | InterruptedException error) {
            throw new ExecutionException("Failed to run codex exec", error);
        } finally {
            cleanupTempDir(tempDir);
        }
    }

    private List<String> buildCommand(Path outputPath, Path schemaPath, List<Path> imageFilePaths) {
        List<String> command = new ArrayList<>();
        command.add("codex");
        command.add("exec");
        command.add("--skip-git-repo-check");
        command.add("--sandbox");
        command.add(System.getenv().getOrDefault("CODEX_SANDBOX_MODE", CodexConstants.DEFAULT_SANDBOX_MODE));
        command.add("--color");
        command.add("never");
        command.add("--output-last-message");
        command.add(outputPath.toString());
        if (schemaPath != null) {
            command.add("--output-schema");
            command.add(schemaPath.toString());
        }
        for (Path imageFilePath : imageFilePaths) {
            command.add("--image");
            command.add(imageFilePath.toString());
        }
        command.add("-");
        return command;
    }

    ProcessExecutor.ProcessResult executeCommand(List<String> command, Path workingDirectory, String prompt, long timeoutSeconds)
        throws IOException, InterruptedException {
        return ProcessExecutor.executeWithInput(
            command,
            workingDirectory,
            prompt,
            StandardCharsets.UTF_8,
            timeoutSeconds
        );
    }

    private void cleanupTempDir(Path tempDir) {
        if (tempDir == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempDir.resolve("reply-schema.json"));
            Files.deleteIfExists(tempDir.resolve("reply.txt"));
            Files.deleteIfExists(tempDir);
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }
}

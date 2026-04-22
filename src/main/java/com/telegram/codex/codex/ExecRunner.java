package com.telegram.codex.codex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.config.AppProperties;
import com.telegram.codex.exception.ExecutionException;
import com.telegram.codex.exception.ExecutionTimeoutException;
import com.telegram.codex.util.StreamUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class ExecRunner {

    private static final String DEFAULT_SANDBOX_MODE = "danger-full-access";

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public ExecRunner(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String run(String prompt, List<Path> imageFilePaths, Map<String, Object> outputSchema) {
        try {
            Path tempDir = Files.createTempDirectory("telegram-codex-");
            Path outputPath = tempDir.resolve("reply.txt");
            Path schemaPath = outputSchema == null ? null : tempDir.resolve("reply-schema.json");
            if (schemaPath != null) {
                Files.writeString(schemaPath, objectMapper.writeValueAsString(outputSchema), StandardCharsets.UTF_8);
            }

            List<String> command = new ArrayList<>();
            command.add("codex");
            command.add("exec");
            command.add("--skip-git-repo-check");
            command.add("--sandbox");
            command.add(System.getenv().getOrDefault("CODEX_SANDBOX_MODE", DEFAULT_SANDBOX_MODE));
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

            Process process = new ProcessBuilder(command)
                .directory(Path.of(".").toFile())
                .start();
            process.getOutputStream().write(prompt.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();

            boolean exited = process.waitFor(properties.getCodexExecTimeoutSeconds(), TimeUnit.SECONDS);
            String stderr = StreamUtils.readStreamToString(process.getErrorStream(), StandardCharsets.UTF_8);
            if (!exited) {
                process.destroyForcibly();
                throw new ExecutionTimeoutException("codex exec timed out after " + properties.getCodexExecTimeoutSeconds() + " seconds");
            }
            if (process.exitValue() != 0) {
                throw new ExecutionException("codex exec failed: " + (stderr.isBlank() ? "unknown error" : stderr.trim()));
            }
            String replyText = Files.readString(outputPath, StandardCharsets.UTF_8).trim();
            if (replyText.isEmpty()) {
                throw new ExecutionException("codex exec returned an empty reply");
            }
            return replyText;
        } catch (ExecutionException error) {
            throw error;
        } catch (Exception error) {
            throw new ExecutionException("Failed to run codex exec", error);
        }
    }
}

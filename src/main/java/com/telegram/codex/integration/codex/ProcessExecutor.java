package com.telegram.codex.integration.codex;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class ProcessExecutor {

    private ProcessExecutor() {
        // Utility class
    }

    public static ProcessResult executeWithInput(List<String> command, Path workingDirectory, String input, Charset charset, long timeoutSeconds) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
            .directory(workingDirectory != null ? workingDirectory.toFile() : null)
            .start();

        if (input != null) {
            process.getOutputStream().write(input.getBytes(charset));
            process.getOutputStream().close();
        }

        boolean exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        String stdout = readStreamToString(process.getInputStream());
        String stderr = readStreamToString(process.getErrorStream());

        if (!exited) {
            process.destroyForcibly();
            return new ProcessResult(-1, stdout, stderr, true);
        }

        return new ProcessResult(process.exitValue(), stdout, stderr, false);
    }

    private static String readStreamToString(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    public record ProcessResult(int exitCode, String stdout, String stderr, boolean timedOut) {
    }
}

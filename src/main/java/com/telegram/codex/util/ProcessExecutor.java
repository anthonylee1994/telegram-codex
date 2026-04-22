package com.telegram.codex.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ProcessExecutor {

    private ProcessExecutor() {
        // Utility class
    }

    public static ProcessResult execute(List<String> command, Path workingDirectory, long timeoutSeconds) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
            .directory(workingDirectory != null ? workingDirectory.toFile() : null)
            .start();

        boolean exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        String stdout = StreamUtils.readStreamToString(process.getInputStream(), StandardCharsets.UTF_8);
        String stderr = StreamUtils.readStreamToString(process.getErrorStream(), StandardCharsets.UTF_8);

        if (!exited) {
            process.destroyForcibly();
            return new ProcessResult(-1, stdout, stderr, true);
        }

        return new ProcessResult(process.exitValue(), stdout, stderr, false);
    }

    public static ProcessResult execute(List<String> command, Path workingDirectory) throws IOException, InterruptedException {
        return execute(command, workingDirectory, 30);
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
        String stdout = StreamUtils.readStreamToString(process.getInputStream(), StandardCharsets.UTF_8);
        String stderr = StreamUtils.readStreamToString(process.getErrorStream(), StandardCharsets.UTF_8);

        if (!exited) {
            process.destroyForcibly();
            return new ProcessResult(-1, stdout, stderr, true);
        }

        return new ProcessResult(process.exitValue(), stdout, stderr, false);
    }

    public record ProcessResult(int exitCode, String stdout, String stderr, boolean timedOut) {
        public boolean success() {
            return exitCode == 0 && !timedOut;
        }
    }
}

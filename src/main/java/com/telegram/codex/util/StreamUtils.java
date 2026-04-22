package com.telegram.codex.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Collectors;

public final class StreamUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamUtils.class);

    private StreamUtils() {
        // Utility class
    }

    public static String readStreamToString(InputStream stream, Charset charset) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, charset))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    public static void deleteDirectoryRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try {
            Files.walk(directory)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException error) {
                        LOGGER.warn("Failed to delete file: {}", path, error);
                    }
                });
        } catch (IOException error) {
            LOGGER.warn("Failed to walk directory for deletion: {}", directory, error);
        }
    }
}

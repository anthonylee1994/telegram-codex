package com.telegram.codex.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

public final class CommandAvailabilityChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandAvailabilityChecker.class);

    private CommandAvailabilityChecker() {
        // Utility class
    }

    public static boolean isAvailable(String command) {
        try {
            ProcessExecutor.ProcessResult result = ProcessExecutor.execute(
                List.of("which", command),
                Path.of("."),
                5
            );
            return result.success();
        } catch (Exception error) {
            LOGGER.debug("Failed to check command availability: {}", command, error);
            return false;
        }
    }
}

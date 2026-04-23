package com.telegram.codex.bootstrap;

import com.telegram.codex.shared.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication(scanBasePackages = "com.telegram.codex")
@EnableConfigurationProperties(AppProperties.class)
public class TelegramCodexApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(TelegramCodexApplication.class);
        createDataDirectory();
        application.run(args);
    }

    private static void createDataDirectory() {
        String configuredPath = System.getenv().getOrDefault("SQLITE_DB_PATH", "./data/app.db");
        Path sqlitePath = Path.of(configuredPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(sqlitePath.getParent());
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Failed to create SQLite directory for " + sqlitePath, error);
        }
    }
}

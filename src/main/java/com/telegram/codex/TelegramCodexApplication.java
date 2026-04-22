package com.telegram.codex;

import com.telegram.codex.config.AppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
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
        } catch (Exception error) {
            throw new IllegalStateException("Failed to create SQLite directory for " + sqlitePath, error);
        }
    }
}

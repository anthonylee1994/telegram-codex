package com.telegram.codex.bootstrap

import com.telegram.codex.shared.AppProperties
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.persistence.autoconfigure.EntityScan
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

@SpringBootApplication(scanBasePackages = ["com.telegram.codex"])
@AutoConfigurationPackage(basePackages = ["com.telegram.codex"])
@EntityScan(basePackages = ["com.telegram.codex"])
@EnableConfigurationProperties(AppProperties::class)
class TelegramCodexApplication

fun main(args: Array<String>) {
    val application = SpringApplication(TelegramCodexApplication::class.java)
    createDataDirectory()
    application.run(*args)
}

private fun createDataDirectory() {
    val configuredPath = System.getenv().getOrDefault("SQLITE_DB_PATH", "./data/app.db")
    val sqlitePath = Path.of(configuredPath).toAbsolutePath().normalize()
    try {
        Files.createDirectories(sqlitePath.parent)
    } catch (error: IOException) {
        throw IllegalStateException("Failed to create SQLite directory for $sqlitePath", error)
    }
}

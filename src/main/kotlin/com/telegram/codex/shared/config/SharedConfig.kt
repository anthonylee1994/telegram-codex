package com.telegram.codex.shared.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.annotation.Validated
import java.net.http.HttpClient

@Validated
@ConfigurationProperties(prefix = "app")
class AppProperties {
    var allowedTelegramUserIds: String = ""

    @field:NotBlank
    lateinit var baseUrl: String

    @field:Min(1)
    var codexExecTimeoutSeconds: Int = 300

    @field:Min(1)
    var maxMediaGroupImages: Int = 10

    @field:Min(1)
    var maxPdfPages: Int = 4

    @field:Min(1)
    var mediaGroupWaitMs: Int = 1200

    @field:Min(1)
    var port: Int = 3000

    @field:Min(1)
    var rateLimitMaxMessages: Int = 5

    @field:Min(1)
    var rateLimitWindowMs: Long = 10_000

    @field:Min(1)
    var sessionTtlDays: Int = 7

    @field:NotBlank
    var sqliteDbPath: String = "./data/app.db"

    @field:NotBlank
    lateinit var telegramBotToken: String

    @field:NotBlank
    lateinit var telegramWebhookSecret: String

    fun allowedTelegramUserIds(): List<String> {
        if (allowedTelegramUserIds.isBlank()) {
            return emptyList()
        }
        return allowedTelegramUserIds.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}

@Configuration
class JacksonConfig {
    @Bean
    fun objectMapper(): ObjectMapper {
        val objectMapper = ObjectMapper()
        objectMapper.registerKotlinModule()
        objectMapper.findAndRegisterModules()
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        return objectMapper
    }

    @Bean
    fun httpClient(): HttpClient = HttpClient.newHttpClient()
}

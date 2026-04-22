package com.telegram.codex.telegram;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.config.AppProperties;
import com.telegram.codex.constants.TelegramConstants;
import com.telegram.codex.util.HttpResponseValidator;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class TelegramApiClient {

    private final AppProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TypingStatusManager typingStatusManager;

    public TelegramApiClient(AppProperties properties, ObjectMapper objectMapper, HttpClient httpClient, TypingStatusManager typingStatusManager) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.typingStatusManager = typingStatusManager;
    }

    public Object postForm(String methodName, Map<String, Object> params) {
        try {
            String body = buildFormBody(params);
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase() + "/" + methodName))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            HttpResponseValidator.validateStatusCode(response.statusCode(), "call Telegram " + methodName);
            Map<String, Object> payload = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            HttpResponseValidator.validateTelegramResponse(payload, methodName);
            return payload.get("result");
        } catch (java.io.IOException | InterruptedException error) {
            throw new IllegalStateException("Failed to call Telegram " + methodName, error);
        }
    }

    public <T> T withTypingStatus(String chatId, Supplier<T> action) {
        return typingStatusManager.withTypingStatus(chatId, id -> sendChatAction(id, "typing"), action);
    }

    public void sendChatAction(String chatId, String action) {
        postForm("sendChatAction", Map.of("chat_id", chatId, "action", action));
    }

    private String buildFormBody(Map<String, Object> params) {
        return params.entrySet().stream()
            .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" +
                URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8))
            .reduce((a, b) -> a + "&" + b)
            .orElse("");
    }

    private String apiBase() {
        return TelegramConstants.TELEGRAM_API_BASE + properties.getTelegramBotToken();
    }
}

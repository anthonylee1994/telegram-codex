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
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class TelegramApiClient {

    private final AppProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TelegramApiClient(AppProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public Object postForm(String methodName, Map<String, Object> params) {
        try {
            StringBuilder body = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (!first) {
                    body.append('&');
                }
                first = false;
                body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                body.append('=');
                body.append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase() + "/" + methodName))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            HttpResponseValidator.validateStatusCode(response.statusCode(), "call Telegram " + methodName);
            Map<String, Object> payload = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            HttpResponseValidator.validateTelegramResponse(payload, methodName);
            return payload.get("result");
        } catch (Exception error) {
            throw new IllegalStateException("Failed to call Telegram " + methodName, error);
        }
    }

    public <T> T withTypingStatus(String chatId, Supplier<T> action) {
        try {
            sendChatAction(chatId, "typing");
        } catch (Exception ignored) {
        }
        Thread thread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    TimeUnit.SECONDS.sleep(4);
                    sendChatAction(chatId, "typing");
                }
            } catch (Exception ignored) {
            }
        });
        thread.setDaemon(true);
        thread.start();
        try {
            return action.get();
        } finally {
            thread.interrupt();
        }
    }

    public void sendChatAction(String chatId, String action) {
        postForm("sendChatAction", Map.of("chat_id", chatId, "action", action));
    }

    public String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to serialize JSON", error);
        }
    }

    private String apiBase() {
        return TelegramConstants.TELEGRAM_API_BASE + properties.getTelegramBotToken();
    }
}

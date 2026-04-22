package com.telegram.codex.telegram;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.codex.ReplyParser;
import com.telegram.codex.config.AppProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TelegramClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramClient.class);
    private static final int MAX_SUGGESTED_REPLIES = 3;

    private final AppProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ReplyParser replyParser;

    public TelegramClient(AppProperties properties, ObjectMapper objectMapper, ReplyParser replyParser) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.replyParser = replyParser;
        this.httpClient = HttpClient.newHttpClient();
    }

    public Path downloadFileToTemp(String fileId) {
        Map<String, Object> file = getFile(fileId);
        String filePath = String.valueOf(file.get("file_path"));
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("telegram-codex-file-");
            Path outputPath = tempDir.resolve(Path.of(filePath).getFileName().toString());
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.telegram.org/file/bot" + properties.getTelegramBotToken() + "/" + filePath)).GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Failed to download Telegram file: " + response.statusCode());
            }
            Files.write(outputPath, response.body());
            return outputPath;
        } catch (Exception error) {
            throw new IllegalStateException("Failed to download Telegram file", error);
        }
    }

    public void sendMessage(String chatId, String text, List<String> suggestedReplies, boolean removeKeyboard) {
        NormalizedReply normalizedReply = normalizeOutboundReply(text, suggestedReplies);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("chat_id", chatId);
        params.put("text", formatTelegramMessage(normalizedReply.text()));
        params.put("parse_mode", "HTML");
        Map<String, Object> replyMarkup = buildReplyMarkup(normalizedReply.suggestedReplies(), removeKeyboard);
        if (replyMarkup != null) {
            params.put("reply_markup", writeJson(replyMarkup));
        }
        postForm("sendMessage", params);
    }

    public void sendChatAction(String chatId, String action) {
        postForm("sendChatAction", Map.of("chat_id", chatId, "action", action));
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

    public void setWebhook(String url, String secretToken) {
        postForm("setWebhook", Map.of("url", url, "secret_token", secretToken));
        LOGGER.info("Telegram webhook configured url={}", url);
    }

    public void setMyCommands(List<Map<String, String>> commands) {
        postForm("setMyCommands", Map.of("commands", writeJson(commands)));
        LOGGER.info("Telegram commands updated count={}", commands.size());
    }

    private Map<String, Object> getFile(String fileId) {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                URI.create(apiBase() + "/getFile?file_id=" + URLEncoder.encode(fileId, StandardCharsets.UTF_8))
            ).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Telegram getFile failed: " + response.statusCode());
            }
            Map<String, Object> payload = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            Map<String, Object> result = castMap(payload.get("result"));
            if (!Boolean.TRUE.equals(payload.get("ok")) || result == null || result.get("file_path") == null) {
                throw new IllegalStateException("Telegram getFile did not include a file path");
            }
            return result;
        } catch (Exception error) {
            throw new IllegalStateException("Failed to call Telegram getFile", error);
        }
    }

    private Object postForm(String methodName, Map<String, Object> params) {
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
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Telegram " + methodName + " failed: " + response.statusCode());
            }
            Map<String, Object> payload = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            if (!Boolean.TRUE.equals(payload.get("ok"))) {
                throw new IllegalStateException("Telegram " + methodName + " returned ok=false");
            }
            return payload.get("result");
        } catch (Exception error) {
            throw new IllegalStateException("Failed to call Telegram " + methodName, error);
        }
    }

    private String apiBase() {
        return "https://api.telegram.org/bot" + properties.getTelegramBotToken();
    }

    private String formatTelegramMessage(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    private Map<String, Object> buildReplyMarkup(List<String> suggestedReplies, boolean removeKeyboard) {
        if (removeKeyboard) {
            return Map.of("remove_keyboard", true);
        }
        ArrayList<String> cleanedReplies = new ArrayList<>();
        for (String reply : suggestedReplies) {
            if (reply == null || reply.isBlank()) {
                continue;
            }
            String normalized = reply.trim();
            if (!cleanedReplies.contains(normalized)) {
                cleanedReplies.add(normalized);
            }
            if (cleanedReplies.size() == MAX_SUGGESTED_REPLIES) {
                break;
            }
        }
        if (cleanedReplies.isEmpty()) {
            return null;
        }
        List<List<Map<String, String>>> keyboard = cleanedReplies.stream()
            .map(reply -> List.of(Map.of("text", reply)))
            .toList();
        return Map.of(
            "keyboard", keyboard,
            "resize_keyboard", true,
            "one_time_keyboard", true
        );
    }

    private NormalizedReply normalizeOutboundReply(String text, List<String> suggestedReplies) {
        ReplyParser.ParsedReply payload = replyParser.parseReply(text);
        String normalizedText = payload.text().isBlank() ? text : payload.text();
        List<String> normalizedSuggestedReplies = suggestedReplies == null || suggestedReplies.isEmpty()
            ? payload.suggestedReplies()
            : suggestedReplies;
        return new NormalizedReply(normalizedText, normalizedSuggestedReplies);
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to serialize JSON", error);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
    }

    private record NormalizedReply(String text, List<String> suggestedReplies) {
    }
}

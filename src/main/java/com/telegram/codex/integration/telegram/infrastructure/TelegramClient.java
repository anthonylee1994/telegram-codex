package com.telegram.codex.integration.telegram.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class TelegramClient implements TelegramGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramClient.class);

    private final TelegramApiClient apiClient;
    private final TelegramFileDownloader fileDownloader;
    private final TelegramMessageFormatter messageFormatter;
    private final ObjectMapper objectMapper;

    public TelegramClient(
        TelegramApiClient apiClient,
        TelegramFileDownloader fileDownloader,
        TelegramMessageFormatter messageFormatter,
        ObjectMapper objectMapper
    ) {
        this.apiClient = apiClient;
        this.fileDownloader = fileDownloader;
        this.messageFormatter = messageFormatter;
        this.objectMapper = objectMapper;
    }

    @Override
    public Path downloadFileToTemp(String fileId) {
        return fileDownloader.downloadFileToTemp(fileId);
    }

    @Override
    public void sendMessage(String chatId, String text, List<String> suggestedReplies, boolean removeKeyboard) {
        TelegramMessageFormatter.NormalizedReply normalizedReply = messageFormatter.normalizeReply(text, suggestedReplies);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("chat_id", chatId);
        // Telegram 最終食 HTML；normalizeReply 先拆 JSON-like payload，再喺呢度做 Telegram-specific formatting。
        params.put("text", messageFormatter.formatForTelegram(normalizedReply.text()));
        params.put("parse_mode", "HTML");
        Map<String, Object> replyMarkup = messageFormatter.buildReplyMarkup(normalizedReply.suggestedReplies(), removeKeyboard);
        if (replyMarkup != null) {
            params.put("reply_markup", writeJson(replyMarkup));
        }
        apiClient.postForm("sendMessage", params);
    }

    @Override
    public <T> T withTypingStatus(String chatId, Supplier<T> action) {
        return apiClient.withTypingStatus(chatId, action);
    }

    @Override
    public void setWebhook(String url, String secretToken) {
        apiClient.postForm("setWebhook", Map.of("url", url, "secret_token", secretToken));
        LOGGER.info("Telegram webhook configured url={}", url);
    }

    @Override
    public void setMyCommands(List<Map<String, String>> commands) {
        apiClient.postForm("setMyCommands", Map.of("commands", writeJson(commands)));
        LOGGER.info("Telegram commands updated count={}", commands.size());
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to serialize JSON", error);
        }
    }
}

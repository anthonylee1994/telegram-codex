package com.telegram.codex.integration.telegram.application.port.out;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public interface TelegramGateway {

    Path downloadFileToTemp(String fileId);

    void sendMessage(String chatId, String text, List<String> suggestedReplies, boolean removeKeyboard);

    <T> T withTypingStatus(String chatId, Supplier<T> action);

    void setWebhook(String url, String secretToken);

    void setMyCommands(List<Map<String, String>> commands);
}

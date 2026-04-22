package com.telegram.codex.conversation.webhooks;

import com.telegram.codex.config.AppProperties;
import com.telegram.codex.conversation.ChatRateLimiter;
import com.telegram.codex.conversation.updates.ProcessedUpdateFlow;
import com.telegram.codex.conversation.updates.ProcessedUpdateRecord;
import com.telegram.codex.telegram.InboundMessage;
import java.util.Optional;
import java.util.regex.Pattern;

import com.telegram.codex.telegram.TelegramWebhookHandler;
import org.springframework.stereotype.Component;

@Component
public class DecisionResolver {

    private static final Pattern START_COMMAND_PATTERN = Pattern.compile("^/start(?:@[\\w_]+)?$", Pattern.UNICODE_CASE);
    private static final Pattern HELP_COMMAND_PATTERN = Pattern.compile("^/help(?:@[\\w_]+)?$", Pattern.UNICODE_CASE);
    private static final Pattern NEW_SESSION_COMMAND_PATTERN = Pattern.compile("^/new(?:@[\\w_]+)?$", Pattern.UNICODE_CASE);
    private static final Pattern FORGET_COMMAND_PATTERN = Pattern.compile("^/forget(?:@[\\w_]+)?$", Pattern.UNICODE_CASE);
    private static final Pattern MEMORY_COMMAND_PATTERN = Pattern.compile("^/memory(?:@[\\w_]+)?$", Pattern.UNICODE_CASE);
    private static final Pattern SESSION_COMMAND_PATTERN = Pattern.compile("^/session(?:@[\\w_]+)?$", Pattern.UNICODE_CASE);
    private static final Pattern STATUS_COMMAND_PATTERN = Pattern.compile("^/status(?:@[\\w_]+)?$", Pattern.UNICODE_CASE);
    private static final Pattern SUMMARY_COMMAND_PATTERN = Pattern.compile("^/summary(?:@[\\w_]+)?$", Pattern.UNICODE_CASE);

    private final AppProperties properties;
    private final ChatRateLimiter rateLimiter;
    private final ProcessedUpdateFlow processedUpdateFlow;

    public DecisionResolver(AppProperties properties, ChatRateLimiter rateLimiter, ProcessedUpdateFlow processedUpdateFlow) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.processedUpdateFlow = processedUpdateFlow;
    }

    public Decision call(InboundMessage message) {
        if (message == null || message.unsupported()) {
            return Decision.unsupported();
        }
        Optional<ProcessedUpdateRecord> processedUpdate = processedUpdateFlow.find(message.updateId());
        if (processedUpdateFlow.duplicate(processedUpdate)) {
            return Decision.duplicate(message);
        }
        if (processedUpdateFlow.replayable(processedUpdate)) {
            return Decision.replay(message, processedUpdate.orElseThrow());
        }
        if (!properties.allowedTelegramUserIds().isEmpty() && !properties.allowedTelegramUserIds().contains(message.userId())) {
            return Decision.rejectUnauthorized(message);
        }
        String text = message.text() == null ? "" : message.text();
        if (NEW_SESSION_COMMAND_PATTERN.matcher(text).matches()) {
            return Decision.resetSession(message, TelegramWebhookHandler.NEW_SESSION_MESSAGE);
        }
        if (START_COMMAND_PATTERN.matcher(text).matches()) {
            return Decision.resetSession(message, TelegramWebhookHandler.START_MESSAGE);
        }
        if (HELP_COMMAND_PATTERN.matcher(text).matches()) {
            return Decision.showHelp(message);
        }
        if (STATUS_COMMAND_PATTERN.matcher(text).matches()) {
            return Decision.showStatus(message);
        }
        if (SESSION_COMMAND_PATTERN.matcher(text).matches()) {
            return Decision.showSession(message);
        }
        if (MEMORY_COMMAND_PATTERN.matcher(text).matches()) {
            return Decision.showMemory(message);
        }
        if (FORGET_COMMAND_PATTERN.matcher(text).matches()) {
            return Decision.resetMemory(message, TelegramWebhookHandler.RESET_MEMORY_MESSAGE);
        }
        if (SUMMARY_COMMAND_PATTERN.matcher(text).matches()) {
            return Decision.summarizeSession(message, TelegramWebhookHandler.SUMMARY_QUEUED_MESSAGE);
        }
        if (message.mediaGroup() && message.imageCount() > properties.getMaxMediaGroupImages()) {
            return Decision.tooManyImages(message, TelegramWebhookHandler.TOO_MANY_IMAGES_MESSAGE);
        }
        if (!rateLimiter.allow(message.chatId())) {
            return Decision.rateLimited(message);
        }
        if (!processedUpdateFlow.beginProcessing(message)) {
            return Decision.duplicate(message);
        }
        return Decision.generateReply(message);
    }
}

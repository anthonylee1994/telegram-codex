package com.telegram.codex.conversation.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.codex.Transcript;
import com.telegram.codex.conversation.ConversationTimeFormatter;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SessionService {

    private static final String SUMMARY_BASELINE_MESSAGE = "以下係之前對話嘅摘要。之後請按呢份摘要延續對話上下文。";

    private final ChatSessionRepository chatSessionRepository;
    private final SessionSummaryClient sessionSummaryClient;
    private final ObjectMapper objectMapper;

    public SessionService(
        ChatSessionRepository chatSessionRepository,
        SessionSummaryClient sessionSummaryClient,
        ObjectMapper objectMapper
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.sessionSummaryClient = sessionSummaryClient;
        this.objectMapper = objectMapper;
    }

    public void persistConversationState(String chatId, String conversationState) {
        chatSessionRepository.persist(chatId, conversationState);
    }

    public void reset(String chatId) {
        chatSessionRepository.reset(chatId);
    }

    public SessionSnapshot snapshot(String chatId) {
        Optional<ChatSessionRecord> maybeSession = chatSessionRepository.findActive(chatId);
        if (maybeSession.isEmpty()) {
            return SessionSnapshot.inactive();
        }
        Transcript transcript = Transcript.fromConversationState(maybeSession.get().lastResponseId(), objectMapper);
        return SessionSnapshot.active(
            transcript.size(),
            (int) Math.ceil(transcript.size() / 2.0),
            ConversationTimeFormatter.format(maybeSession.get().updatedAt())
        );
    }

    public SessionSummaryResult summarize(String chatId) {
        Optional<ChatSessionRecord> maybeSession = chatSessionRepository.findActive(chatId);
        if (maybeSession.isEmpty()) {
            return SessionSummaryResult.missingSession();
        }
        Transcript transcript = Transcript.fromConversationState(maybeSession.get().lastResponseId(), objectMapper);
        if (transcript.size() < 4) {
            return SessionSummaryResult.tooShort(transcript.size());
        }
        String summaryText = sessionSummaryClient.summarize(transcript);
        Transcript summaryTranscript = Transcript.empty()
            .append("user", SUMMARY_BASELINE_MESSAGE)
            .append("assistant", summaryText);
        chatSessionRepository.persist(chatId, summaryTranscript.toConversationState(objectMapper));
        return SessionSummaryResult.ok(transcript.size(), summaryText);
    }
}

package com.telegram.codex.conversation.application.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.conversation.domain.ConversationTimeFormatter;
import com.telegram.codex.conversation.domain.session.ChatSessionRecord;
import com.telegram.codex.conversation.domain.session.Transcript;
import com.telegram.codex.conversation.domain.ConversationConstants;
import com.telegram.codex.conversation.domain.MessageConstants;
import com.telegram.codex.conversation.infrastructure.session.ChatSessionRepository;
import com.telegram.codex.conversation.infrastructure.session.CodexSessionCompactClient;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final CodexSessionCompactClient sessionCompactClient;
    private final ObjectMapper objectMapper;

    public SessionService(
        ChatSessionRepository chatSessionRepository,
        CodexSessionCompactClient sessionCompactClient,
        ObjectMapper objectMapper
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.sessionCompactClient = sessionCompactClient;
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

    public SessionCompactResult compact(String chatId) {
        Optional<ChatSessionRecord> maybeSession = chatSessionRepository.findActive(chatId);
        if (maybeSession.isEmpty()) {
            return SessionCompactResult.missingSession();
        }
        Transcript transcript = Transcript.fromConversationState(maybeSession.get().lastResponseId(), objectMapper);
        if (transcript.size() < ConversationConstants.MIN_TRANSCRIPT_SIZE_FOR_COMPACT) {
            return SessionCompactResult.tooShort(transcript.size());
        }
        String compactText = sessionCompactClient.compact(transcript);
        Transcript compactTranscript = Transcript.empty()
            .append("user", MessageConstants.COMPACT_BASELINE_MESSAGE)
            .append("assistant", compactText);
        chatSessionRepository.persist(chatId, compactTranscript.toConversationState(objectMapper));
        return SessionCompactResult.ok(transcript.size(), compactText);
    }

    public record SessionSnapshot(boolean active, int messageCount, int turnCount, String lastUpdatedAt) {

        public static SessionSnapshot inactive() {
            return new SessionSnapshot(false, 0, 0, null);
        }

        public static SessionSnapshot active(int messageCount, int turnCount, String lastUpdatedAt) {
            return new SessionSnapshot(true, messageCount, turnCount, lastUpdatedAt);
        }
    }

    public record SessionCompactResult(Status status, Integer messageCount, Integer originalMessageCount, String compactText) {

        public enum Status {
            MISSING_SESSION,
            TOO_SHORT,
            OK
        }

        public static SessionCompactResult missingSession() {
            return new SessionCompactResult(Status.MISSING_SESSION, null, null, null);
        }

        public static SessionCompactResult tooShort(int messageCount) {
            return new SessionCompactResult(Status.TOO_SHORT, messageCount, null, null);
        }

        public static SessionCompactResult ok(int originalMessageCount, String compactText) {
            return new SessionCompactResult(Status.OK, null, originalMessageCount, compactText);
        }
    }
}

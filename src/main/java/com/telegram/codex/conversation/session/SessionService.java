package com.telegram.codex.conversation.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.codex.Transcript;
import com.telegram.codex.constants.ConversationConstants;
import com.telegram.codex.constants.MessageConstants;
import com.telegram.codex.conversation.ConversationTimeFormatter;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final SessionCompactClient sessionCompactClient;
    private final ObjectMapper objectMapper;

    public SessionService(
        ChatSessionRepository chatSessionRepository,
        SessionCompactClient sessionCompactClient,
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
}

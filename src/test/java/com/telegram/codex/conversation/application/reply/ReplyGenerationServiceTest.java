package com.telegram.codex.conversation.application.reply;

import com.telegram.codex.conversation.application.ProcessedUpdateService;
import com.telegram.codex.conversation.application.gateway.ReplyGenerationGateway;
import com.telegram.codex.conversation.application.session.SessionService;
import com.telegram.codex.conversation.domain.memory.ChatMemoryRecord;
import com.telegram.codex.conversation.domain.session.ChatSessionRecord;
import com.telegram.codex.conversation.infrastructure.memory.ChatMemoryRepository;
import com.telegram.codex.conversation.infrastructure.memory.CodexMemoryClient;
import com.telegram.codex.conversation.infrastructure.session.ChatSessionRepository;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplyGenerationServiceTest {

    @Test
    void handleUsesStoredConversationState() {
        ReplyGenerationGateway cliClient = Mockito.mock(ReplyGenerationGateway.class);
        CodexMemoryClient memoryClient = Mockito.mock(CodexMemoryClient.class);
        ProcessedUpdateService processedUpdateService = Mockito.mock(ProcessedUpdateService.class);
        SessionService sessionService = Mockito.mock(SessionService.class);
        TelegramGateway telegramGateway = Mockito.mock(TelegramGateway.class);
        AttachmentDownloader attachmentDownloader = Mockito.mock(AttachmentDownloader.class);
        when(cliClient.generateReply(anyString(), any(), anyList(), any(), any())).thenReturn(new ReplyResult("next-state", List.of("a", "b", "c"), "reply"));
        ChatSessionRepository sessionRepository = mockSessionRepository(Optional.of(new ChatSessionRecord("3", "[{\"role\":\"user\",\"content\":\"hi\"}]", System.currentTimeMillis())));
        ChatMemoryRepository memoryRepository = Mockito.mock(ChatMemoryRepository.class);
        when(memoryRepository.find("3")).thenReturn(Optional.of(new ChatMemoryRecord("3", "記憶", System.currentTimeMillis())));
        when(memoryClient.merge("記憶", "你好", "reply")).thenReturn("記憶");
        when(attachmentDownloader.downloadImages(List.of())).thenReturn(List.of());
        when(telegramGateway.withTypingStatus(eq("3"), any())).thenAnswer(invocation -> invocation.getArgument(1, Supplier.class).get());

        ReplyGenerationService service = new ReplyGenerationService(
            cliClient,
            sessionRepository,
            memoryRepository,
            memoryClient,
            processedUpdateService,
            sessionService,
            telegramGateway,
            attachmentDownloader
        );

        service.handle(buildMessage());

        verify(processedUpdateService).pruneIfNeeded();
        verify(processedUpdateService).savePendingReply(99L, "3", 10L, new ReplyResult("next-state", List.of("a", "b", "c"), "reply"));
        verify(telegramGateway).sendMessage("3", "reply", List.of("a", "b", "c"), false);
        verify(sessionService).persistConversationState("3", "next-state");
        verify(processedUpdateService).markProcessed(99L, "3", 10L);
        verify(attachmentDownloader).downloadImages(List.of());
        verify(attachmentDownloader).cleanup(List.of());
    }

    @Test
    void handleSkipsLongTermMemoryRefreshForBlankUserText() {
        ReplyGenerationGateway cliClient = Mockito.mock(ReplyGenerationGateway.class);
        ProcessedUpdateService processedUpdateService = Mockito.mock(ProcessedUpdateService.class);
        SessionService sessionService = Mockito.mock(SessionService.class);
        TelegramGateway telegramGateway = Mockito.mock(TelegramGateway.class);
        AttachmentDownloader attachmentDownloader = Mockito.mock(AttachmentDownloader.class);
        CodexMemoryClient memoryClient = Mockito.mock(CodexMemoryClient.class);
        ChatMemoryRepository memoryRepository = Mockito.mock(ChatMemoryRepository.class);
        when(cliClient.generateReply(any(), any(), any(), any(), any())).thenReturn(new ReplyResult("next-state", List.of(), "reply"));
        when(memoryRepository.find("3")).thenReturn(Optional.empty());
        when(attachmentDownloader.downloadImages(List.of())).thenReturn(List.of());
        when(telegramGateway.withTypingStatus(eq("3"), any())).thenAnswer(invocation -> invocation.getArgument(1, Supplier.class).get());
        ChatSessionRepository sessionRepository = mockSessionRepository(Optional.empty());

        ReplyGenerationService service = new ReplyGenerationService(
            cliClient,
            sessionRepository,
            memoryRepository,
            memoryClient,
            processedUpdateService,
            sessionService,
            telegramGateway,
            attachmentDownloader
        );

        service.handle(new InboundMessage("3", List.of(), null, 10, List.of(), List.of(), null, null, "   ", "5", 99));

        verify(memoryClient, never()).merge(anyString(), anyString(), anyString());
        verify(memoryRepository, never()).persist(anyString(), anyString());
    }

    private ChatSessionRepository mockSessionRepository(Optional<ChatSessionRecord> record) {
        ChatSessionRepository repository = Mockito.mock(ChatSessionRepository.class);
        when(repository.findActive("3")).thenReturn(record);
        return repository;
    }

    private InboundMessage buildMessage() {
        return new InboundMessage("3", List.of(), null, 10, List.of(), List.of(), null, null, "你好", "5", 99);
    }
}

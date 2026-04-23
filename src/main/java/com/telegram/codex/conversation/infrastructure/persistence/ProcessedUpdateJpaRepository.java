package com.telegram.codex.conversation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProcessedUpdateJpaRepository extends JpaRepository<ProcessedUpdateEntity, Long> {

    long deleteBySentAtIsNotNullAndProcessedAtLessThan(long cutoff);

    List<ProcessedUpdateEntity> findByChatId(String chatId);
}

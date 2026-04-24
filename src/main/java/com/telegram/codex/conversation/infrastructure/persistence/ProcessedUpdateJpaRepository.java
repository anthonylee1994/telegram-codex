package com.telegram.codex.conversation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedUpdateJpaRepository extends JpaRepository<ProcessedUpdateEntity, Long> {

    long deleteBySentAtIsNotNullAndProcessedAtLessThan(long cutoff);
}

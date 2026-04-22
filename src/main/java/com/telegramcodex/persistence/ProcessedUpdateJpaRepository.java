package com.telegramcodex.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedUpdateJpaRepository extends JpaRepository<ProcessedUpdateEntity, Long> {

    long deleteBySentAtIsNotNullAndProcessedAtLessThan(long cutoff);

    List<ProcessedUpdateEntity> findByChatId(String chatId);
}

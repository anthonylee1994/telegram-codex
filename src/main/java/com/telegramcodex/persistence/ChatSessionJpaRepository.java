package com.telegramcodex.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionJpaRepository extends JpaRepository<ChatSessionEntity, String> {
}

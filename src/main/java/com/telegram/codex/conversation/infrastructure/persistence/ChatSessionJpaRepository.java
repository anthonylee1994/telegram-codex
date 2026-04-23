package com.telegram.codex.conversation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionJpaRepository extends JpaRepository<ChatSessionEntity, String> {
}

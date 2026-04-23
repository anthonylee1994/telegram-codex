package com.telegram.codex.conversation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMemoryJpaRepository extends JpaRepository<ChatMemoryEntity, String> {
}

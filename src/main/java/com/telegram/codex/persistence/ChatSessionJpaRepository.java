package com.telegram.codex.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionJpaRepository extends JpaRepository<ChatSessionEntity, String> {
}

package com.telegram.codex.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMemoryJpaRepository extends JpaRepository<ChatMemoryEntity, String> {
}

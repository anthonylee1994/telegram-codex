package com.telegram.codex.conversation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaGroupMessageJpaRepository extends JpaRepository<MediaGroupMessageEntity, Long> {

    List<MediaGroupMessageEntity> findByMediaGroupKeyOrderByMessageIdAscUpdateIdAsc(String mediaGroupKey);

    void deleteByMediaGroupKey(String mediaGroupKey);
}

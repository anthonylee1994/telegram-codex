package com.telegramcodex.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaGroupMessageJpaRepository extends JpaRepository<MediaGroupMessageEntity, Long> {

    List<MediaGroupMessageEntity> findByMediaGroupKeyOrderByMessageIdAscUpdateIdAsc(String mediaGroupKey);

    void deleteByMediaGroupKey(String mediaGroupKey);
}

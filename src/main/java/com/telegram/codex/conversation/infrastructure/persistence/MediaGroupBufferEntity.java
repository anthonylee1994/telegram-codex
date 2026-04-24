package com.telegram.codex.conversation.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "media_group_buffers")
@SuppressWarnings("JpaDataSourceORMInspection")
public class MediaGroupBufferEntity {

    @Id
    @Column(name = "key", nullable = false)
    private String key;

    @Column(name = "deadline_at", nullable = false)
    private long deadlineAt;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public long getDeadlineAt() {
        return deadlineAt;
    }

    public void setDeadlineAt(long deadlineAt) {
        this.deadlineAt = deadlineAt;
    }
}

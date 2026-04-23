package com.telegram.codex.conversation.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "media_group_messages")
public class MediaGroupMessageEntity {

    @Id
    @Column(name = "update_id", nullable = false)
    private Long updateId;

    @Column(name = "media_group_key", nullable = false, columnDefinition = "text")
    private String mediaGroupKey;

    @Column(name = "message_id", nullable = false)
    private long messageId;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    public Long getUpdateId() {
        return updateId;
    }

    public void setUpdateId(Long updateId) {
        this.updateId = updateId;
    }

    public String getMediaGroupKey() {
        return mediaGroupKey;
    }

    public void setMediaGroupKey(String mediaGroupKey) {
        this.mediaGroupKey = mediaGroupKey;
    }

    public long getMessageId() {
        return messageId;
    }

    public void setMessageId(long messageId) {
        this.messageId = messageId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}

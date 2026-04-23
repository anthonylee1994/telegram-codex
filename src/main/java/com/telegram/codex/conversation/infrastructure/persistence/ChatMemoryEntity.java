package com.telegram.codex.conversation.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_memories")
public class ChatMemoryEntity {

    @Id
    @Column(name = "chat_id", nullable = false)
    private String chatId;

    @Column(name = "memory_text", columnDefinition = "text")
    private String memoryText;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public String getMemoryText() {
        return memoryText;
    }

    public void setMemoryText(String memoryText) {
        this.memoryText = memoryText;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}

package com.telegramcodex.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "processed_updates")
public class ProcessedUpdateEntity {

    @Id
    @Column(name = "update_id", nullable = false)
    private Long updateId;

    @Column(name = "chat_id", nullable = false)
    private String chatId;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "processed_at", nullable = false)
    private long processedAt;

    @Column(name = "reply_text", columnDefinition = "text")
    private String replyText;

    @Column(name = "conversation_state", columnDefinition = "text")
    private String conversationState;

    @Column(name = "suggested_replies", columnDefinition = "text")
    private String suggestedReplies;

    @Column(name = "sent_at")
    private Long sentAt;

    public Long getUpdateId() {
        return updateId;
    }

    public void setUpdateId(Long updateId) {
        this.updateId = updateId;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public long getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(long processedAt) {
        this.processedAt = processedAt;
    }

    public String getReplyText() {
        return replyText;
    }

    public void setReplyText(String replyText) {
        this.replyText = replyText;
    }

    public String getConversationState() {
        return conversationState;
    }

    public void setConversationState(String conversationState) {
        this.conversationState = conversationState;
    }

    public String getSuggestedReplies() {
        return suggestedReplies;
    }

    public void setSuggestedReplies(String suggestedReplies) {
        this.suggestedReplies = suggestedReplies;
    }

    public Long getSentAt() {
        return sentAt;
    }

    public void setSentAt(Long sentAt) {
        this.sentAt = sentAt;
    }
}

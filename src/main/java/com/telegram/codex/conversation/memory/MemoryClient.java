package com.telegram.codex.conversation.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.codex.ExecRunner;
import com.telegram.codex.exception.ExecutionException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MemoryClient {

    private final ExecRunner execRunner;
    private final ObjectMapper objectMapper;

    public MemoryClient(ExecRunner execRunner, ObjectMapper objectMapper) {
        this.execRunner = execRunner;
        this.objectMapper = objectMapper;
    }

    public String merge(String existingMemory, String userMessage, String assistantReply) {
        String rawReply = execRunner.run(buildPrompt(existingMemory, userMessage, assistantReply), List.of(), memoryOutputSchema());
        try {
            Map<String, Object> payload = objectMapper.readValue(rawReply, new TypeReference<>() {
            });
            return String.valueOf(payload.getOrDefault("memory", "")).trim();
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new ExecutionException("memory merge returned invalid JSON", error);
        }
    }

    private String buildPrompt(String existingMemory, String userMessage, String assistantReply) {
        return String.join("\n",
            "你而家負責維護一份 Telegram 用戶嘅長期記憶。",
            "只可以輸出一個 JSON object，格式一定要係 {\"memory\":\"...\"}。",
            "memory 只可以記錄長期有用、同用戶本人有關、之後值得帶入新對話嘅資訊。",
            "可以保留：長期偏好、身份背景、持續目標、固定限制、慣用語言。",
            "唔好保留：一次性任務、短期上下文、臨時問題、敏感憑證、原文長段摘錄。",
            "如果新訊息修正咗舊資料，要用新資料覆蓋舊資料。",
            "如果冇任何值得保留嘅內容，而現有記憶亦唔需要改，就原樣輸出現有記憶。",
            "如果所有記憶都應該刪除，就輸出空字串。",
            "記憶內容要簡潔，最好用 1 至 5 行 bullet points，每行一點，用廣東話。",
            "",
            "現有記憶：",
            existingMemory == null || existingMemory.isBlank() ? "（冇）" : existingMemory,
            "",
            "最新用戶訊息：",
            userMessage == null || userMessage.isBlank() ? "（冇）" : userMessage,
            "",
            "助手回覆：",
            assistantReply == null || assistantReply.isBlank() ? "（冇）" : assistantReply
        );
    }

    private Map<String, Object> memoryOutputSchema() {
        return Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("memory"),
            "properties", Map.of("memory", Map.of("type", "string"))
        );
    }
}

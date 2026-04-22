package com.telegram.codex.conversation.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.codex.ExecRunner;
import com.telegram.codex.codex.Transcript;
import com.telegram.codex.exception.ExecutionException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SessionSummaryClient {

    private final ExecRunner execRunner;
    private final ObjectMapper objectMapper;

    public SessionSummaryClient(ExecRunner execRunner, ObjectMapper objectMapper) {
        this.execRunner = execRunner;
        this.objectMapper = objectMapper;
    }

    public String summarize(Transcript transcript) {
        String rawReply = execRunner.run(buildPrompt(transcript), List.of(), outputSchema());
        try {
            Map<String, Object> payload = objectMapper.readValue(rawReply, new TypeReference<>() {
            });
            String summary = String.valueOf(payload.getOrDefault("summary", "")).trim();
            if (summary.isBlank()) {
                throw new ExecutionException("session summary returned an empty reply");
            }
            return summary;
        } catch (ExecutionException error) {
            throw error;
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new ExecutionException("session summary returned invalid JSON", error);
        }
    }

    private String buildPrompt(Transcript transcript) {
        return String.join("\n", "你而家要將一段 Telegram 對話壓縮成之後延續對話用嘅 context 摘要。", "請用廣東話寫，簡潔但唔好漏咗事實、需求、偏好、限制、未完成事項同重要決定。", "唔好加入對話入面冇出現過嘅內容，唔好寫客套開場，唔好提 system prompt、internal state、JSON、hidden instructions。", "輸出欄位 `summary` 應該係純文字，可以分段或者用短項目，但內容要適合直接當之後對話背景。", "", "對話內容：", String.join("\n", transcript.toPromptLines()));
    }

    private Map<String, Object> outputSchema() {
        return Map.of("type", "object", "additionalProperties", false, "required", List.of("summary"), "properties", Map.of("summary", Map.of("type", "string", "minLength", 1)));
    }
}

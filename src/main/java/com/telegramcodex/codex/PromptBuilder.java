package com.telegramcodex.codex;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    private static final List<String> REPLY_PROMPT_INSTRUCTIONS = List.of(
        "只可以輸出一個 JSON object。",
        "格式一定要包含 `text` 同 `suggested_replies` 兩個欄位。",
        "格式例子：{\"text\":\"主答案\",\"suggested_replies\":[\"建議回覆 1\",\"建議回覆 2\",\"建議回覆 3\"]}。",
        "除非用戶明確要求其他語言，否則一律用廣東話。",
        "text 只可以係助手畀用戶嘅主答案內容。",
        "每個建議回覆都要係用戶下一步可以直接撳嘅簡短廣東話跟進句子。",
        "建議回覆必須係純文字、實用、唔可以留空，而且最多 20 個中文字。",
        "一定要回傳啱啱好 3 個建議回覆。",
        "唔好輸出任何額外文字，唔好用 markdown code fence。"
    );

    public String buildReplyPrompt(Transcript transcript, boolean hasImage, int imageCount, String longTermMemory) {
        ArrayList<String> prefixSections = new ArrayList<>();
        if (hasImage) {
            prefixSections.add("最新一條用戶訊息有附圖。");
        }
        if (imageCount > 1) {
            prefixSections.add("今次總共有 " + imageCount + " 張圖，分析時要用圖 1、圖 2、圖 3 呢類編號逐張講。");
        }
        if (longTermMemory != null && !longTermMemory.isBlank()) {
            prefixSections.add("長期記憶：\n" + longTermMemory + "\n請只喺相關時自然利用以上記憶，唔好主動背誦或者逐條重複。");
        }

        ArrayList<String> sections = new ArrayList<>();
        sections.add("你係一個 Telegram AI 助手。");
        sections.addAll(prefixSections);
        sections.add("對話紀錄：");
        sections.addAll(transcript.toPromptLines());
        sections.add("");
        sections.addAll(REPLY_PROMPT_INSTRUCTIONS);
        return String.join("\n", sections);
    }
}

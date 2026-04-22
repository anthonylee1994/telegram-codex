package com.telegram.codex.documents;

import org.springframework.stereotype.Component;

@Component
public class TextDocumentPromptBuilder {

    public String buildPrompt(String userText, String fileName, String fileContent, boolean isTruncated, boolean isReplyingToFile) {
        String basePrompt = determineBasePrompt(userText, isReplyingToFile);
        String normalizedFileName = fileName == null ? "未命名檔案" : fileName;
        String truncationNotice = isTruncated ? "注意：檔案內容已經截短，只包含前面一部分。" : "";

        return String.join("\n",
            basePrompt,
            "",
            "檔案名稱：" + normalizedFileName,
            "以下係檔案內容：",
            "```text",
            fileContent,
            "```",
            truncationNotice
        ).trim();
    }

    private String determineBasePrompt(String userText, boolean isReplyingToFile) {
        if (userText != null && !userText.isBlank()) {
            return userText;
        }
        if (isReplyingToFile) {
            return "我引用咗之前一份文字檔。請先概括內容，再按內容回答。";
        }
        return "我上載咗一份文字檔。請先概括內容，再按內容回答。";
    }
}

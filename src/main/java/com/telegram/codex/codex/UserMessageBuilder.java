package com.telegram.codex.codex;

import com.telegram.codex.util.StringUtils;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

@Component
public class UserMessageBuilder {

    public String buildUserMessage(String text, List<Path> imageFilePaths, String replyToText) {
        String baseText = text == null ? "" : text;
        if (baseText.isBlank() && !imageFilePaths.isEmpty()) {
            baseText = buildUnpromptedImageMessage(imageFilePaths.size());
        }
        if (StringUtils.isNullOrBlank(replyToText)) {
            return baseText;
        }
        return buildReplyMessage(baseText, replyToText);
    }

    private String buildReplyMessage(String baseText, String replyToText) {
        return String.join("\n",
            "你而家係回覆緊之前一則訊息。",
            "被引用訊息：" + replyToText,
            "你今次嘅新訊息：" + (baseText.isBlank() ? "（冇文字）" : baseText)
        );
    }

    private String buildUnpromptedImageMessage(int imageCount) {
        if (imageCount == 1) {
            return "我上載咗 1 張圖。請先描述圖 1，再按內容幫我分析重點。";
        }
        String labels = String.join("、", IntStream.rangeClosed(1, imageCount).mapToObj(index -> "圖 " + index).toList());
        return "我上載咗 " + imageCount + " 張圖。請按 " + labels + " 逐張描述，再比較異同同整理重點。";
    }
}

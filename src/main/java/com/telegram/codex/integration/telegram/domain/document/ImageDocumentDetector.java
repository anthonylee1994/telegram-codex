package com.telegram.codex.integration.telegram.domain.document;

import com.telegram.codex.integration.telegram.domain.TelegramPayloadValueReader;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ImageDocumentDetector implements DocumentTypeDetector {

    @Override
    public boolean supports(Map<String, Object> document) {
        if (document == null || document.get("file_id") == null) {
            return false;
        }
        String mimeType = TelegramPayloadValueReader.stringValue(document.get("mime_type")).toLowerCase();
        if (DocumentConstants.IMAGE_MIME_TYPE_PREFIXES.stream().anyMatch(mimeType::startsWith)) {
            return true;
        }
        String fileName = TelegramPayloadValueReader.stringValue(document.get("file_name")).toLowerCase();
        return DocumentConstants.IMAGE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    @Override
    public String getFileId(Map<String, Object> document) {
        return TelegramPayloadValueReader.stringValue(document.get("file_id"));
    }
}

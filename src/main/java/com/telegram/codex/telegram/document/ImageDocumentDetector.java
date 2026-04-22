package com.telegram.codex.telegram.document;

import com.telegram.codex.constants.DocumentConstants;
import com.telegram.codex.util.MapUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ImageDocumentDetector implements DocumentTypeDetector {

    @Override
    public boolean supports(Map<String, Object> document) {
        if (document == null || document.get("file_id") == null) {
            return false;
        }
        String mimeType = MapUtils.stringValue(document.get("mime_type")).toLowerCase();
        if (DocumentConstants.IMAGE_MIME_TYPE_PREFIXES.stream().anyMatch(mimeType::startsWith)) {
            return true;
        }
        String fileName = MapUtils.stringValue(document.get("file_name")).toLowerCase();
        return DocumentConstants.IMAGE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    @Override
    public String getFileId(Map<String, Object> document) {
        return MapUtils.stringValue(document.get("file_id"));
    }
}

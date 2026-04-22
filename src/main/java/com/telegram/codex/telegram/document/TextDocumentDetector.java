package com.telegram.codex.telegram.document;

import com.telegram.codex.constants.DocumentConstants;
import com.telegram.codex.util.MapUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TextDocumentDetector implements DocumentTypeDetector {

    private final ImageDocumentDetector imageDetector;
    private final PdfDocumentDetector pdfDetector;

    public TextDocumentDetector(ImageDocumentDetector imageDetector, PdfDocumentDetector pdfDetector) {
        this.imageDetector = imageDetector;
        this.pdfDetector = pdfDetector;
    }

    @Override
    public boolean supports(Map<String, Object> document) {
        if (document == null || document.get("file_id") == null) {
            return false;
        }
        if (imageDetector.supports(document) || pdfDetector.supports(document)) {
            return false;
        }
        String mimeType = MapUtils.stringValue(document.get("mime_type")).toLowerCase();
        if (DocumentConstants.SUPPORTED_TEXT_MIME_TYPES.contains(mimeType)) {
            return true;
        }
        String fileName = MapUtils.stringValue(document.get("file_name")).toLowerCase();
        return DocumentConstants.SUPPORTED_TEXT_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    @Override
    public String getFileId(Map<String, Object> document) {
        return MapUtils.stringValue(document.get("file_id"));
    }
}

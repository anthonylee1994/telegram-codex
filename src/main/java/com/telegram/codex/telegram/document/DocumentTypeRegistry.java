package com.telegram.codex.telegram.document;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DocumentTypeRegistry {

    private final ImageDocumentDetector imageDetector;

    public DocumentTypeRegistry(ImageDocumentDetector imageDetector) {
        this.imageDetector = imageDetector;
    }

    public boolean isImageDocument(Map<String, Object> document) {
        return imageDetector.supports(document);
    }
}

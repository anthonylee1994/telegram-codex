package com.telegram.codex.telegram.document;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DocumentTypeRegistry {

    private final ImageDocumentDetector imageDetector;
    private final PdfDocumentDetector pdfDetector;
    private final TextDocumentDetector textDetector;

    public DocumentTypeRegistry(
        ImageDocumentDetector imageDetector,
        PdfDocumentDetector pdfDetector,
        TextDocumentDetector textDetector
    ) {
        this.imageDetector = imageDetector;
        this.pdfDetector = pdfDetector;
        this.textDetector = textDetector;
    }

    public Optional<DocumentTypeDetector> findDetector(Map<String, Object> document) {
        List<DocumentTypeDetector> detectors = List.of(imageDetector, pdfDetector, textDetector);
        return detectors.stream()
            .filter(detector -> detector.supports(document))
            .findFirst();
    }

    public boolean isImageDocument(Map<String, Object> document) {
        return imageDetector.supports(document);
    }

    public boolean isPdfDocument(Map<String, Object> document) {
        return pdfDetector.supports(document);
    }

    public boolean isTextDocument(Map<String, Object> document) {
        return textDetector.supports(document);
    }
}

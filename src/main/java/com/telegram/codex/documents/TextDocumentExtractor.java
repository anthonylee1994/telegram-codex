package com.telegram.codex.documents;

import com.telegram.codex.constants.DocumentConstants;
import com.telegram.codex.exception.MissingDependencyException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

@Component
public class TextDocumentExtractor {

    private final List<FileTypeExtractor> extractors;

    public TextDocumentExtractor(
        DocxExtractor docxExtractor,
        XlsxExtractor xlsxExtractor,
        HtmlExtractor htmlExtractor,
        PlainTextExtractor plainTextExtractor
    ) {
        this.extractors = List.of(docxExtractor, xlsxExtractor, htmlExtractor, plainTextExtractor);
    }

    public ExtractionResult extract(Path filePath) {
        try {
            String rawContent = extractRawContent(filePath);
            boolean truncatedByBytes = rawContent.getBytes(StandardCharsets.UTF_8).length > DocumentConstants.MAX_DOCUMENT_BYTES;
            if (truncatedByBytes) {
                rawContent = new String(rawContent.getBytes(StandardCharsets.UTF_8), 0, DocumentConstants.MAX_DOCUMENT_BYTES, StandardCharsets.UTF_8);
            }
            String normalized = rawContent.strip();
            boolean truncatedByChars = normalized.length() > DocumentConstants.MAX_DOCUMENT_CHARS;
            if (truncatedByChars) {
                normalized = normalized.substring(0, DocumentConstants.MAX_DOCUMENT_CHARS).stripTrailing();
            }
            return new ExtractionResult(normalized, truncatedByBytes || truncatedByChars);
        } catch (MissingDependencyException error) {
            throw error;
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Failed to extract text document", error);
        }
    }

    private String extractRawContent(Path filePath) throws Exception {
        String fileName = filePath.getFileName().toString().toLowerCase();
        for (FileTypeExtractor extractor : extractors) {
            if (extractor.supports(fileName)) {
                return extractor.extract(filePath);
            }
        }
        throw new IllegalStateException("No extractor found for file: " + fileName);
    }

    public record ExtractionResult(String content, boolean truncated) {
    }
}

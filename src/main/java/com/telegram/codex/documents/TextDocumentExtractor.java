package com.telegram.codex.documents;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.StreamSupport;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

@Component
public class TextDocumentExtractor {

    public static class MissingDependencyError extends RuntimeException {
        public MissingDependencyError(String message) {
            super(message);
        }
    }

    private static final int DEFAULT_MAX_BYTES = 200_000;
    private static final int DEFAULT_MAX_CHARS = 12_000;

    public ExtractionResult extract(Path filePath) {
        try {
            String rawContent = extractRawContent(filePath);
            boolean truncatedByBytes = rawContent.getBytes(StandardCharsets.UTF_8).length > DEFAULT_MAX_BYTES;
            if (truncatedByBytes) {
                rawContent = new String(rawContent.getBytes(StandardCharsets.UTF_8), 0, DEFAULT_MAX_BYTES, StandardCharsets.UTF_8);
            }
            String normalized = rawContent.strip();
            boolean truncatedByChars = normalized.length() > DEFAULT_MAX_CHARS;
            if (truncatedByChars) {
                normalized = normalized.substring(0, DEFAULT_MAX_CHARS).stripTrailing();
            }
            return new ExtractionResult(normalized, truncatedByBytes || truncatedByChars);
        } catch (MissingDependencyError error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Failed to extract text document", error);
        }
    }

    private String extractRawContent(Path filePath) throws Exception {
        String fileName = filePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".docx")) {
            try (InputStream inputStream = Files.newInputStream(filePath);
                 XWPFDocument document = new XWPFDocument(OPCPackage.open(inputStream));
                 XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                return extractor.getText();
            }
        }
        if (fileName.endsWith(".xlsx")) {
            StringBuilder builder = new StringBuilder();
            try (InputStream inputStream = Files.newInputStream(filePath); XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
                DataFormatter formatter = new DataFormatter();
                for (int index = 0; index < workbook.getNumberOfSheets(); index += 1) {
                    XSSFSheet sheet = workbook.getSheetAt(index);
                    builder.append("[Sheet ").append(index + 1).append("]\n");
                    sheet.forEach(row -> {
                        String rowText = row.cellIterator().hasNext()
                            ? StreamSupport.stream(row.spliterator(), false)
                                .map(formatter::formatCellValue)
                                .filter(value -> !value.isBlank())
                                .reduce((left, right) -> left + "\t" + right)
                                .orElse("")
                            : "";
                        if (!rowText.isBlank()) {
                            builder.append(rowText).append('\n');
                        }
                    });
                    builder.append('\n');
                }
            }
            return builder.toString();
        }
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        if (fileName.endsWith(".html")) {
            return Jsoup.parse(content).text();
        }
        return content;
    }

    public record ExtractionResult(String content, boolean truncated) {
    }
}

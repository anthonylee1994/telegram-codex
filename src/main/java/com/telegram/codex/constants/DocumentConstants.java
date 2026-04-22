package com.telegram.codex.constants;

import java.util.List;

public final class DocumentConstants {

    private DocumentConstants() {
        // Constants class
    }

    // MIME types
    public static final List<String> SUPPORTED_TEXT_MIME_TYPES = List.of(
        "text/plain",
        "text/markdown",
        "text/html",
        "application/xhtml+xml",
        "application/json",
        "text/csv",
        "application/csv",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    public static final String PDF_MIME_TYPE = "application/pdf";
    public static final List<String> IMAGE_MIME_TYPE_PREFIXES = List.of("image/");

    // File extensions
    public static final List<String> SUPPORTED_TEXT_EXTENSIONS = List.of(
        ".txt", ".md", ".html", ".json", ".csv", ".docx", ".xlsx"
    );

    public static final String PDF_EXTENSION = ".pdf";
    public static final List<String> IMAGE_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".webp");

    // Document processing limits
    public static final int MAX_DOCUMENT_BYTES = 200_000;
    public static final int MAX_DOCUMENT_CHARS = 12_000;
}

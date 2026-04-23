package com.telegram.codex.integration.telegram.domain.document;

import java.util.List;

public final class DocumentConstants {

    private DocumentConstants() {
        // Constants class
    }

    public static final List<String> IMAGE_MIME_TYPE_PREFIXES = List.of("image/");
    public static final List<String> IMAGE_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".webp");
}

package com.telegram.codex.integration.telegram.domain.document;

import java.util.Map;

public interface DocumentTypeDetector {

    boolean supports(Map<String, Object> document);

    String getFileId(Map<String, Object> document);
}

package com.telegram.codex.telegram.document;

import java.util.Map;

public interface DocumentTypeDetector {

    boolean supports(Map<String, Object> document);

    String getFileId(Map<String, Object> document);
}

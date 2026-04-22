package com.telegram.codex.documents;

import java.nio.file.Path;

public interface FileTypeExtractor {
    boolean supports(String fileName);
    String extract(Path filePath) throws Exception;
}

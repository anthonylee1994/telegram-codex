package com.telegram.codex.documents;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class PlainTextExtractor implements FileTypeExtractor {

    @Override
    public boolean supports(String fileName) {
        return true; // Fallback for all other files
    }

    @Override
    public String extract(Path filePath) throws Exception {
        return Files.readString(filePath, StandardCharsets.UTF_8);
    }
}

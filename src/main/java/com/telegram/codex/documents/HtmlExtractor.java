package com.telegram.codex.documents;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class HtmlExtractor implements FileTypeExtractor {

    @Override
    public boolean supports(String fileName) {
        return fileName.endsWith(".html");
    }

    @Override
    public String extract(Path filePath) throws Exception {
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        return Jsoup.parse(content).text();
    }
}

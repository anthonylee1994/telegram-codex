package com.telegram.codex.documents;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class DocxExtractor implements FileTypeExtractor {

    @Override
    public boolean supports(String fileName) {
        return fileName.endsWith(".docx");
    }

    @Override
    public String extract(Path filePath) throws Exception {
        try (InputStream inputStream = Files.newInputStream(filePath);
             XWPFDocument document = new XWPFDocument(OPCPackage.open(inputStream));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }
}

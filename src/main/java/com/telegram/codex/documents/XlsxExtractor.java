package com.telegram.codex.documents;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class XlsxExtractor implements FileTypeExtractor {

    @Override
    public boolean supports(String fileName) {
        return fileName.endsWith(".xlsx");
    }

    @Override
    public String extract(Path filePath) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (InputStream inputStream = Files.newInputStream(filePath);
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            DataFormatter formatter = new DataFormatter();
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                XSSFSheet sheet = workbook.getSheetAt(index);
                builder.append("[Sheet ").append(index + 1).append("]\n");
                sheet.forEach(row -> {
                    String rowText = row.cellIterator().hasNext()
                        ? StreamSupport.stream(row.spliterator(), false)
                            .map(formatter::formatCellValue)
                            .filter(value -> !value.isBlank())
                            .collect(Collectors.joining("\t"))
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
}

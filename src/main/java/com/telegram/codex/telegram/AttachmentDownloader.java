package com.telegram.codex.telegram;

import com.telegram.codex.documents.PdfPageRasterizer;
import com.telegram.codex.util.StreamUtils;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class AttachmentDownloader {

    private final TelegramClient telegramClient;
    private final PdfPageRasterizer pdfPageRasterizer;

    public AttachmentDownloader(TelegramClient telegramClient, PdfPageRasterizer pdfPageRasterizer) {
        this.telegramClient = telegramClient;
        this.pdfPageRasterizer = pdfPageRasterizer;
    }

    public List<Path> downloadImages(List<String> imageFileIds) {
        ArrayList<Path> imagePaths = new ArrayList<>();
        for (String imageFileId : imageFileIds) {
            imagePaths.add(telegramClient.downloadFileToTemp(imageFileId));
        }
        return List.copyOf(imagePaths);
    }

    public List<Path> downloadAndRasterizePdf(String pdfFileId) {
        Path pdfPath = telegramClient.downloadFileToTemp(pdfFileId);
        return pdfPageRasterizer.rasterize(pdfPath);
    }

    public List<Path> downloadAllAttachments(List<String> imageFileIds, String pdfFileId) {
        ArrayList<Path> allImages = new ArrayList<>(downloadImages(imageFileIds));
        if (pdfFileId != null) {
            allImages.addAll(downloadAndRasterizePdf(pdfFileId));
        }
        return List.copyOf(allImages);
    }

    public void cleanup(List<Path> filePaths) {
        for (Path filePath : filePaths) {
            StreamUtils.deleteDirectoryRecursively(filePath.getParent());
        }
    }
}

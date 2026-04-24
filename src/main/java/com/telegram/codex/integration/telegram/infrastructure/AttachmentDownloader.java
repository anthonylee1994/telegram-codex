package com.telegram.codex.integration.telegram.infrastructure;

import com.telegram.codex.conversation.application.gateway.AttachmentDownloadGateway;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class AttachmentDownloader implements AttachmentDownloadGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttachmentDownloader.class);

    private final TelegramGateway telegramClient;

    public AttachmentDownloader(TelegramGateway telegramClient) {
        this.telegramClient = telegramClient;
    }

    @Override
    public List<Path> downloadImages(List<String> imageFileIds) {
        ArrayList<Path> imagePaths = new ArrayList<>();
        for (String imageFileId : imageFileIds) {
            imagePaths.add(telegramClient.downloadFileToTemp(imageFileId));
        }
        return List.copyOf(imagePaths);
    }

    @Override
    public void cleanup(List<Path> filePaths) {
        Set<Path> uniqueParentDirs = new HashSet<>();
        for (Path filePath : filePaths) {
            if (filePath != null && filePath.getParent() != null) {
                uniqueParentDirs.add(filePath.getParent());
            }
        }
        for (Path parentDir : uniqueParentDirs) {
            deleteDirectoryRecursively(parentDir);
        }
    }

    private void deleteDirectoryRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try {
            Files.walk(directory)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException error) {
                        LOGGER.warn("Failed to delete file: {}", path, error);
                    }
                });
        } catch (IOException error) {
            LOGGER.warn("Failed to walk directory for deletion: {}", directory, error);
        }
    }
}

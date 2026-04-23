package com.telegram.codex.conversation.application.port.out;

import java.nio.file.Path;
import java.util.List;

public interface AttachmentDownloadPort {

    List<Path> downloadImages(List<String> imageFileIds);

    void cleanup(List<Path> filePaths);
}

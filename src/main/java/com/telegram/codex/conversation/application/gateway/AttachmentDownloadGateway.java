package com.telegram.codex.conversation.application.gateway;

import java.nio.file.Path;
import java.util.List;

public interface AttachmentDownloadGateway {

    List<Path> downloadImages(List<String> imageFileIds);

    void cleanup(List<Path> filePaths);
}

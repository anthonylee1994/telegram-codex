package com.telegram.codex.documents;

import com.telegram.codex.config.AppProperties;
import com.telegram.codex.constants.CodexConstants;
import com.telegram.codex.exception.MissingDependencyException;
import com.telegram.codex.exception.RasterizationException;
import com.telegram.codex.util.CommandAvailabilityChecker;
import com.telegram.codex.util.ProcessExecutor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Component
public class PdfPageRasterizer {

    private final AppProperties properties;

    public PdfPageRasterizer(AppProperties properties) {
        this.properties = properties;
    }

    public List<Path> rasterize(Path pdfPath) {
        if (pdfPath == null) {
            throw new RasterizationException("pdf path is missing");
        }
        if (!CommandAvailabilityChecker.isAvailable(CodexConstants.PDFTOPPM_COMMAND)) {
            throw new MissingDependencyException(CodexConstants.PDFTOPPM_COMMAND, "pdftoppm is not installed");
        }
        try {
            Path outputPrefix = pdfPath.getParent().resolve("pdf-page");
            List<String> command = List.of(
                CodexConstants.PDFTOPPM_COMMAND,
                "-f", "1",
                "-l", String.valueOf(properties.getMaxPdfPages()),
                "-png",
                pdfPath.toString(),
                outputPrefix.toString()
            );

            ProcessExecutor.ProcessResult result = ProcessExecutor.execute(command, pdfPath.getParent(), 30);

            if (!result.success()) {
                String errorMessage = result.timedOut()
                    ? "pdftoppm timed out"
                    : "pdftoppm failed: " + result.stderr();
                throw new RasterizationException(errorMessage);
            }

            try (Stream<Path> paths = Files.list(pdfPath.getParent())) {
                List<Path> imagePaths = paths
                    .filter(path -> path.getFileName().toString().startsWith("pdf-page-") && path.getFileName().toString().endsWith(".png"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
                if (imagePaths.isEmpty()) {
                    throw new RasterizationException("pdf conversion did not produce any page images");
                }
                return imagePaths;
            }
        } catch (RasterizationException | MissingDependencyException error) {
            throw error;
        } catch (IOException | InterruptedException error) {
            throw new RasterizationException("Failed to rasterize PDF", error);
        }
    }
}

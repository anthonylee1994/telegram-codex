package com.telegram.codex.documents;

import com.telegram.codex.config.AppProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Component
public class PdfPageRasterizer {

    public static class RasterizationError extends RuntimeException {
        public RasterizationError(String message) {
            super(message);
        }

        public RasterizationError(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class MissingDependencyError extends RasterizationError {
        public MissingDependencyError(String message) {
            super(message);
        }
    }

    private final AppProperties properties;

    public PdfPageRasterizer(AppProperties properties) {
        this.properties = properties;
    }

    public List<Path> rasterize(Path pdfPath) {
        if (pdfPath == null) {
            throw new RasterizationError("pdf path is missing");
        }
        if (!commandAvailable("pdftoppm")) {
            throw new MissingDependencyError("pdftoppm is not installed");
        }
        try {
            Path outputPrefix = pdfPath.getParent().resolve("pdf-page");
            Process process = new ProcessBuilder(
                "pdftoppm",
                "-f", "1",
                "-l", String.valueOf(properties.getMaxPdfPages()),
                "-png",
                pdfPath.toString(),
                outputPrefix.toString()
            ).start();
            if (process.waitFor() != 0) {
                throw new RasterizationError("pdftoppm failed");
            }
            try (Stream<Path> paths = Files.list(pdfPath.getParent())) {
                List<Path> imagePaths = paths
                    .filter(path -> path.getFileName().toString().startsWith("pdf-page-") && path.getFileName().toString().endsWith(".png"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
                if (imagePaths.isEmpty()) {
                    throw new RasterizationError("pdf conversion did not produce any page images");
                }
                return imagePaths;
            }
        } catch (RasterizationError error) {
            throw error;
        } catch (Exception error) {
            throw new RasterizationError("Failed to rasterize PDF", error);
        }
    }

    private boolean commandAvailable(String command) {
        try {
            return new ProcessBuilder("which", command).start().waitFor() == 0;
        } catch (Exception error) {
            return false;
        }
    }
}

package com.telegram.codex.documents;

import com.telegram.codex.config.AppProperties;
import com.telegram.codex.exception.MissingDependencyException;
import com.telegram.codex.exception.RasterizationException;
import org.springframework.stereotype.Component;

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
        if (!commandAvailable("pdftoppm")) {
            throw new MissingDependencyException("pdftoppm", "pdftoppm is not installed");
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
                throw new RasterizationException("pdftoppm failed");
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
        } catch (RasterizationException error) {
            throw error;
        } catch (Exception error) {
            throw new RasterizationException("Failed to rasterize PDF", error);
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

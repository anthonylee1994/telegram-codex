package com.telegram.codex.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureTest {

    private static final Path MAIN_JAVA_DIR = Path.of("src/main/java/com/telegram/codex");

    @Test
    void conversationApplicationDoesNotDependOnInfrastructureImplementations() {
        assertNoForbiddenImports(
            MAIN_JAVA_DIR.resolve("conversation/application"),
            List.of(
                "import com.telegram.codex.integration.telegram.infrastructure.",
                "import com.telegram.codex.integration.codex."
            )
        );
    }

    @Test
    void interfacesDoNotDependOnInfrastructureImplementations() {
        assertNoForbiddenImports(
            MAIN_JAVA_DIR.resolve("interfaces"),
            List.of(
                "import com.telegram.codex.conversation.infrastructure.",
                "import com.telegram.codex.integration.telegram.infrastructure.",
                "import com.telegram.codex.integration.codex."
            )
        );
    }

    @Test
    void integrationApplicationDoesNotDependOnIntegrationInfrastructure() {
        assertNoForbiddenImports(
            MAIN_JAVA_DIR.resolve("integration/telegram/application"),
            List.of("import com.telegram.codex.integration.telegram.infrastructure.")
        );
    }

    @Test
    void mainCodeDoesNotIntroduceLegacyStoreTypeNames() {
        List<String> violations = findDeclarationViolations(
            Pattern.compile("\\b(class|interface|record)\\s+[A-Za-z0-9_]*Store\\b")
        );
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    private void assertNoForbiddenImports(Path root, List<String> forbiddenImports) {
        List<String> violations = findViolations(root, forbiddenImports);
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    private List<String> findViolations(Path root, List<String> forbiddenImports) {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                .filter(path -> path.toString().endsWith(".java"))
                .flatMap(path -> forbiddenImports.stream()
                    .filter(forbiddenImport -> fileContains(path, forbiddenImport))
                    .map(forbiddenImport -> path + " contains forbidden import: " + forbiddenImport))
                .toList();
        } catch (IOException error) {
            throw new IllegalStateException("Failed to scan architecture paths under " + root, error);
        }
    }

    private List<String> findDeclarationViolations(Pattern forbiddenDeclarationPattern) {
        try (Stream<Path> stream = Files.walk(MAIN_JAVA_DIR)) {
            return stream
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> fileMatches(path, forbiddenDeclarationPattern))
                .map(path -> path + " declares forbidden legacy *Store type name")
                .toList();
        } catch (IOException error) {
            throw new IllegalStateException("Failed to scan declaration names under " + MAIN_JAVA_DIR, error);
        }
    }

    private boolean fileContains(Path path, String needle) {
        try {
            return Files.readString(path).contains(needle);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read " + path, error);
        }
    }

    private boolean fileMatches(Path path, Pattern pattern) {
        try {
            return pattern.matcher(Files.readString(path)).find();
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read " + path, error);
        }
    }
}

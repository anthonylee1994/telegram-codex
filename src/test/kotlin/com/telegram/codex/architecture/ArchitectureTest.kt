package com.telegram.codex.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.readText

class ArchitectureTest {
    @Test
    fun conversationApplicationDoesNotDependOnInfrastructureImplementations() {
        assertNoForbiddenImports(
            MAIN_KOTLIN_DIR.resolve("conversation/application"),
            listOf(
                "import com.telegram.codex.integration.telegram.infrastructure.",
                "import com.telegram.codex.integration.codex.",
            ),
        )
    }

    @Test
    fun interfacesDoNotDependOnInfrastructureImplementations() {
        assertNoForbiddenImports(
            MAIN_KOTLIN_DIR.resolve("interfaces"),
            listOf(
                "import com.telegram.codex.conversation.infrastructure.",
                "import com.telegram.codex.integration.telegram.infrastructure.",
                "import com.telegram.codex.integration.codex.",
            ),
        )
    }

    @Test
    fun integrationApplicationDoesNotDependOnIntegrationInfrastructure() {
        assertNoForbiddenImports(
            MAIN_KOTLIN_DIR.resolve("integration/telegram/application"),
            listOf("import com.telegram.codex.integration.telegram.infrastructure."),
        )
    }

    @Test
    fun mainCodeDoesNotIntroduceLegacyStoreTypeNames() {
        val violations = findDeclarationViolations(Pattern.compile("\\b(class|interface|data\\s+class|object)\\s+[A-Za-z0-9_]*Store\\b"))
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    private fun assertNoForbiddenImports(root: Path, forbiddenImports: List<String>) {
        val violations = findViolations(root, forbiddenImports)
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    private fun findViolations(root: Path, forbiddenImports: List<String>): List<String> =
        Files.walk(root).use { stream ->
            stream
                .filter { it.toString().endsWith(".kt") }
                .flatMap { path ->
                    forbiddenImports.stream()
                        .filter { forbiddenImport -> fileContains(path, forbiddenImport) }
                        .map { forbiddenImport -> "$path contains forbidden import: $forbiddenImport" }
                }
                .toList()
        }

    private fun findDeclarationViolations(forbiddenDeclarationPattern: Pattern): List<String> =
        Files.walk(MAIN_KOTLIN_DIR).use { stream ->
            stream
                .filter { it.toString().endsWith(".kt") }
                .filter { fileMatches(it, forbiddenDeclarationPattern) }
                .map { "$it declares forbidden legacy *Store type name" }
                .toList()
        }

    private fun fileContains(path: Path, needle: String): Boolean = path.readText().contains(needle)
    private fun fileMatches(path: Path, pattern: Pattern): Boolean = pattern.matcher(path.readText()).find()

    companion object {
        private val MAIN_KOTLIN_DIR = Path.of("src/main/kotlin/com/telegram/codex")
    }
}

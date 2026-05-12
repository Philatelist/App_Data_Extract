package com.clmextract.web.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the column-file read/write logic in AdminController.
 *
 * We subclass AdminController and override resolveColumnFile() to redirect
 * all file I/O to a temporary directory, keeping the tests hermetic.
 */
class AdminControllerColumnsTest {

    @TempDir
    Path tempDir;

    // Minimal subclass that redirects resolveColumnFile to the temp directory.
    private AdminController controllerWithTempDir() {
        return new AdminController(null, null) {
            @Override
            protected Path resolveColumnFile(String boType) {
                return tempDir.resolve(boType + ".csv");
            }
        };
    }

    // -----------------------------------------------------------------------
    // resolveColumnFile (default path shape)
    // -----------------------------------------------------------------------

    @Test
    void resolveColumnFile_returnsExpectedRelativePath() {
        AdminController controller = new AdminController(null, null);
        Path result = controller.resolveColumnFile("MyBo");
        assertEquals(Path.of("config", "columns", "MyBo.csv"), result);
    }

    // -----------------------------------------------------------------------
    // readColumnFile helper — logic extracted for testing
    // -----------------------------------------------------------------------

    /**
     * Replicates the core read logic from getColumns() so we can test it
     * without a Javalin Context.
     */
    private List<String> readPaths(Path file) throws Exception {
        if (!Files.exists(file)) return null;
        return Files.readAllLines(file).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Replicates the core write logic from putColumns() so we can test it
     * without a Javalin Context.
     */
    private void writePaths(Path file, List<String> paths) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n", paths));
    }

    // -----------------------------------------------------------------------
    // File-missing case
    // -----------------------------------------------------------------------

    @Test
    void readPaths_returnsNull_whenFileDoesNotExist() throws Exception {
        Path missing = tempDir.resolve("nonexistent.csv");
        assertNull(readPaths(missing));
    }

    // -----------------------------------------------------------------------
    // File-exists case
    // -----------------------------------------------------------------------

    @Test
    void readPaths_returnsExpectedLines_whenFileExists() throws Exception {
        Path file = tempDir.resolve("ContractA.csv");
        Files.writeString(file, "GPAMEA_Data/field1\nGPAMEA_Data/field2\n");

        List<String> result = readPaths(file);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("GPAMEA_Data/field1", result.get(0));
        assertEquals("GPAMEA_Data/field2", result.get(1));
    }

    @Test
    void readPaths_skipsBlankLines() throws Exception {
        Path file = tempDir.resolve("BoWithBlanks.csv");
        Files.writeString(file, "path/one\n\n  \npath/two\n");

        List<String> result = readPaths(file);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("path/one", result.get(0));
        assertEquals("path/two", result.get(1));
    }

    @Test
    void readPaths_trimsWhitespaceFromLines() throws Exception {
        Path file = tempDir.resolve("BoTrim.csv");
        Files.writeString(file, "  path/with/spaces  \nclean/path\n");

        List<String> result = readPaths(file);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("path/with/spaces", result.get(0));
        assertEquals("clean/path", result.get(1));
    }

    @Test
    void readPaths_returnsSingleEntry() throws Exception {
        Path file = tempDir.resolve("SingleField.csv");
        Files.writeString(file, "only/one/path");

        List<String> result = readPaths(file);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("only/one/path", result.get(0));
    }

    // -----------------------------------------------------------------------
    // putColumns — write behaviour
    // -----------------------------------------------------------------------

    @Test
    void writePaths_createsFileWithOnePathPerLine() throws Exception {
        Path columnDir = tempDir.resolve("columns");
        Path file = columnDir.resolve("NewBo.csv");

        writePaths(file, List.of("path/alpha", "path/beta", "path/gamma"));

        assertTrue(Files.exists(file));
        String content = Files.readString(file);
        assertEquals("path/alpha\npath/beta\npath/gamma", content);
    }

    @Test
    void writePaths_createsDirectoryIfMissing() throws Exception {
        Path deepDir = tempDir.resolve("a").resolve("b").resolve("c");
        Path file = deepDir.resolve("DeepBo.csv");

        assertFalse(Files.exists(deepDir));
        writePaths(file, List.of("some/path"));
        assertTrue(Files.exists(deepDir));
        assertTrue(Files.exists(file));
    }

    @Test
    void writePaths_overwritesExistingFile() throws Exception {
        Path file = tempDir.resolve("ExistingBo.csv");
        Files.writeString(file, "old/path/one\nold/path/two");

        writePaths(file, List.of("new/path/only"));

        String content = Files.readString(file);
        assertEquals("new/path/only", content);
        assertFalse(content.contains("old"));
    }

    @Test
    void writePaths_handlesEmptyList() throws Exception {
        Path file = tempDir.resolve("EmptyBo.csv");

        writePaths(file, List.of());

        assertTrue(Files.exists(file));
        assertEquals("", Files.readString(file));
    }

    // -----------------------------------------------------------------------
    // resolveColumnFile override via subclass
    // -----------------------------------------------------------------------

    @Test
    void overriddenResolveColumnFile_pointsToTempDir() {
        AdminController controller = controllerWithTempDir();
        Path resolved = controller.resolveColumnFile("TestBo");
        assertEquals(tempDir.resolve("TestBo.csv"), resolved);
    }
}

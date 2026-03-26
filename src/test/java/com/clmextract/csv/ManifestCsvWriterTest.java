package com.clmextract.csv;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManifestCsvWriterTest {

    @TempDir
    Path tempDir;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // -----------------------------------------------------------------------
    // In-memory appender (no external dependency)
    // -----------------------------------------------------------------------

    static final class CapturingAppender extends AbstractAppender {
        private final List<LogEvent> events = Collections.synchronizedList(new ArrayList<>());

        CapturingAppender(String name) {
            super(name, null, null, true, null);
        }

        @Override
        public void append(LogEvent event) {
            // Store an immutable copy so it survives log4j2 object reuse
            events.add(event.toImmutable());
        }

        List<LogEvent> getEvents() {
            return Collections.unmodifiableList(events);
        }

        void clear() {
            events.clear();
        }
    }

    private CapturingAppender capturingAppender;

    @BeforeEach
    void installAppender() {
        capturingAppender = new CapturingAppender("CapturingAppender");
        capturingAppender.start();

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        config.addAppender(capturingAppender);

        // Attach to the com.clmextract logger (or root if not present)
        LoggerConfig loggerConfig = config.getLoggerConfig("com.clmextract");
        loggerConfig.addAppender(capturingAppender, Level.DEBUG, null);
        ctx.updateLoggers();
    }

    @AfterEach
    void removeAppender() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("com.clmextract");
        loggerConfig.removeAppender("CapturingAppender");
        ctx.updateLoggers();
        capturingAppender.stop();
    }

    private long countWarnMessages(String fragment) {
        return capturingAppender.getEvents().stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .filter(e -> e.getMessage().getFormattedMessage().contains(fragment))
                .count();
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parse a naive CSV line (no quoted commas, no embedded newlines) split on delimiter.
     */
    private String[] splitCsv(String line, char delimiter) {
        return line.split(String.valueOf(delimiter), -1);
    }

    // -----------------------------------------------------------------------
    // Slice 1 tests
    // -----------------------------------------------------------------------

    @Test
    void testHeaderAlwaysPresent() throws IOException {
        Path manifestPath = tempDir.resolve("manifest.csv");
        // Empty dir — manifest itself doesn't exist yet so only the manifest will be present
        // after write; but we haven't written any other files, so data rows = 0.
        ManifestCsvWriter writer = new ManifestCsvWriter(tempDir, manifestPath, ',');
        writer.write();

        assertTrue(Files.exists(manifestPath));
        String[] lines = Files.readString(manifestPath).trim().split("\n");
        assertEquals(1, lines.length, "Only header row expected for empty directory");
        String[] header = splitCsv(lines[0].trim(), ',');
        assertEquals("Filename", header[0]);
        assertEquals("SHA256", header[1]);
        assertEquals("SizeBytes", header[2]);
        assertEquals("GeneratedAt", header[3]);
    }

    @Test
    void testCorrectRowCountAndFilenames() throws IOException {
        // Create 3 known files
        byte[] content1 = "hello world".getBytes(StandardCharsets.UTF_8);
        byte[] content2 = "foo bar baz".getBytes(StandardCharsets.UTF_8);
        byte[] content3 = "12345\n67890".getBytes(StandardCharsets.UTF_8);

        Path file1 = tempDir.resolve("alpha.txt");
        Path file2 = tempDir.resolve("beta.txt");
        Path file3 = tempDir.resolve("gamma.txt");
        Files.write(file1, content1);
        Files.write(file2, content2);
        Files.write(file3, content3);

        Path manifestPath = tempDir.resolve("manifest.csv");
        ManifestCsvWriter writer = new ManifestCsvWriter(tempDir, manifestPath, ',');
        writer.write();

        String[] lines = Files.readString(manifestPath).trim().split("\n");
        // 1 header + 3 data rows
        assertEquals(4, lines.length, "Expected header + 3 data rows");

        // Collect filenames from data rows
        java.util.Set<String> filenames = new java.util.HashSet<>();
        for (int i = 1; i < lines.length; i++) {
            String[] cols = splitCsv(lines[i].trim(), ',');
            filenames.add(cols[0]);
        }
        assertTrue(filenames.contains("alpha.txt"));
        assertTrue(filenames.contains("beta.txt"));
        assertTrue(filenames.contains("gamma.txt"));
    }

    @Test
    void testSha256ValuesAreCorrect() throws IOException {
        byte[] content1 = "hello world".getBytes(StandardCharsets.UTF_8);
        byte[] content2 = "foo bar baz".getBytes(StandardCharsets.UTF_8);

        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Files.write(file1, content1);
        Files.write(file2, content2);

        Path manifestPath = tempDir.resolve("manifest.csv");
        ManifestCsvWriter writer = new ManifestCsvWriter(tempDir, manifestPath, ',');
        writer.write();

        String[] lines = Files.readString(manifestPath).trim().split("\n");
        // Build map filename -> sha256 from manifest
        java.util.Map<String, String> manifestSha = new java.util.HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String[] cols = splitCsv(lines[i].trim(), ',');
            manifestSha.put(cols[0], cols[1]);
        }

        assertEquals(sha256Hex(content1), manifestSha.get("file1.txt"),
                "SHA-256 for file1.txt must match independent computation");
        assertEquals(sha256Hex(content2), manifestSha.get("file2.txt"),
                "SHA-256 for file2.txt must match independent computation");
    }

    @Test
    void testSizeBytesIsCorrect() throws IOException {
        byte[] content = "size test content".getBytes(StandardCharsets.UTF_8);
        Path file = tempDir.resolve("sizefile.txt");
        Files.write(file, content);

        Path manifestPath = tempDir.resolve("manifest.csv");
        ManifestCsvWriter writer = new ManifestCsvWriter(tempDir, manifestPath, ',');
        writer.write();

        String[] lines = Files.readString(manifestPath).trim().split("\n");
        assertEquals(2, lines.length);
        String[] cols = splitCsv(lines[1].trim(), ',');
        long reportedSize = Long.parseLong(cols[2]);
        assertEquals(Files.size(file), reportedSize, "SizeBytes must equal Files.size()");
    }

    @Test
    void testGeneratedAtIsParseable() throws IOException {
        byte[] content = "timestamp test".getBytes(StandardCharsets.UTF_8);
        Path file = tempDir.resolve("tsfile.txt");
        Files.write(file, content);

        Path manifestPath = tempDir.resolve("manifest.csv");
        ManifestCsvWriter writer = new ManifestCsvWriter(tempDir, manifestPath, ',');
        writer.write();

        String[] lines = Files.readString(manifestPath).trim().split("\n");
        assertEquals(2, lines.length);
        String[] cols = splitCsv(lines[1].trim(), ',');
        String generatedAt = cols[3];

        // Must parse without throwing
        LocalDateTime parsed = LocalDateTime.parse(generatedAt, TIMESTAMP_FORMAT);
        assertNotNull(parsed, "GeneratedAt must be a valid yyyy-MM-dd HH:mm:ss timestamp");
    }

    @Test
    void testManifestFileExcludedFromOwnEntries() throws IOException {
        byte[] content = "some data".getBytes(StandardCharsets.UTF_8);
        Path file1 = tempDir.resolve("data1.txt");
        Path file2 = tempDir.resolve("data2.txt");
        Files.write(file1, content);
        Files.write(file2, content);

        Path manifestPath = tempDir.resolve("manifest.csv");
        ManifestCsvWriter writer = new ManifestCsvWriter(tempDir, manifestPath, ',');
        writer.write();

        String manifestContent = Files.readString(manifestPath);
        assertFalse(manifestContent.contains("manifest.csv"),
                "Manifest must not list itself as an entry");

        String[] lines = manifestContent.trim().split("\n");
        // header + 2 data files (manifest itself excluded)
        assertEquals(3, lines.length);
    }

    @Test
    void testCustomDelimiterIsUsed() throws IOException {
        byte[] content = "delim test".getBytes(StandardCharsets.UTF_8);
        Path file = tempDir.resolve("delimfile.txt");
        Files.write(file, content);

        Path manifestPath = tempDir.resolve("manifest.csv");
        ManifestCsvWriter writer = new ManifestCsvWriter(tempDir, manifestPath, ';');
        writer.write();

        String[] lines = Files.readString(manifestPath).trim().split("\n");
        // Header line should use semicolons, not commas
        assertTrue(lines[0].contains(";"), "Header should use semicolon delimiter");
        assertFalse(lines[0].contains(","), "Header should not contain commas when delimiter is semicolon");

        String[] headerCols = splitCsv(lines[0].trim(), ';');
        assertEquals("Filename", headerCols[0]);
        assertEquals("SHA256", headerCols[1]);
        assertEquals("SizeBytes", headerCols[2]);
        assertEquals("GeneratedAt", headerCols[3]);
    }

    // -----------------------------------------------------------------------
    // Slice 2 tests
    // -----------------------------------------------------------------------

    /**
     * Task 1: write() on an empty directory produces only a header row, no exception.
     */
    @Test
    void testEmptyDirectoryProducesHeaderOnly() {
        Path manifestPath = tempDir.resolve("manifest.csv");
        ManifestCsvWriter writer = new ManifestCsvWriter(tempDir, manifestPath, ',');

        // Must not throw
        assertDoesNotThrow(writer::write);

        assertTrue(Files.exists(manifestPath), "Manifest file must be created");
        String content;
        try {
            content = Files.readString(manifestPath).trim();
        } catch (IOException e) {
            fail("Could not read manifest: " + e.getMessage());
            return;
        }
        String[] lines = content.split("\n");
        assertEquals(1, lines.length, "Only header row expected for empty directory");
        String[] header = splitCsv(lines[0].trim(), ',');
        assertEquals("Filename", header[0]);
        assertEquals("SHA256", header[1]);
        assertEquals("SizeBytes", header[2]);
        assertEquals("GeneratedAt", header[3]);
    }

    /**
     * Task 4: Simulates failure on attempts 1 and 2, succeeds on attempt 3.
     * Expects the manifest to be correctly produced and two WARN log entries for failed attempts.
     */
    @Test
    void testRetrySucceedsOnThirdAttempt() throws IOException {
        Path dataFile = tempDir.resolve("data.txt");
        Files.write(dataFile, "retry test content".getBytes(StandardCharsets.UTF_8));

        Path manifestPath = tempDir.resolve("manifest.csv");

        ManifestCsvWriter writer = new ManifestCsvWriter(tempDir, manifestPath, ',') {
            private int callCount = 0;

            @Override
            protected void doWrite() throws IOException {
                callCount++;
                if (callCount <= 2) {
                    throw new IOException("simulated failure on attempt " + callCount);
                }
                super.doWrite();
            }
        };

        // Must not throw
        assertDoesNotThrow(writer::write);

        // Manifest must exist and be valid
        assertTrue(Files.exists(manifestPath), "Manifest file must exist after retry success");
        String[] lines = Files.readString(manifestPath).trim().split("\n");
        // header + 1 data file
        assertEquals(2, lines.length, "Expected header + 1 data row");
        String[] cols = splitCsv(lines[1].trim(), ',');
        assertEquals("data.txt", cols[0]);

        // Two WARN entries for the two failed attempts
        long warnCount = countWarnMessages("Manifest write attempt");
        assertEquals(2, warnCount, "Expected 2 WARN log entries for 2 failed attempts");
    }

    /**
     * Task 5: Simulates persistent IOException across all 3 attempts.
     * Expects no exception to propagate and a final WARN log entry.
     */
    @Test
    void testAllRetriesFailSwallowsException() {
        Path manifestPath = tempDir.resolve("manifest.csv");

        ManifestCsvWriter writer = new ManifestCsvWriter(tempDir, manifestPath, ',') {
            @Override
            protected void doWrite() throws IOException {
                throw new IOException("persistent failure");
            }
        };

        // Must not throw any exception
        assertDoesNotThrow(writer::write);

        // Three per-attempt WARNs
        long attemptWarnCount = countWarnMessages("Manifest write attempt");
        assertEquals(3, attemptWarnCount, "Expected 3 WARN log entries for 3 failed attempts");

        // Final "skipping" WARN
        long skippingWarnCount = countWarnMessages("Manifest generation failed after 3 attempts");
        assertEquals(1, skippingWarnCount, "Expected 1 final WARN log entry about skipping");
    }
}

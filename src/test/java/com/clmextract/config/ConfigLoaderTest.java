package com.clmextract.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private String writeYaml(Path dir, String content) throws IOException {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, content);
        return file.toString();
    }

    // ------------------------------------------------------------------
    // 1. Valid config loads successfully
    // ------------------------------------------------------------------

    @Test
    void testValidConfigLoadsSuccessfully() throws IOException {
        String yaml = """
                server:
                  url: "http://localhost:8080/clm/rest/methods"
                  username: "admin"
                  password: "admin"
                endpointsFile: "inputs/endpoints.yml"
                boTypes:
                  - name: "Contract"
                    trackingFilter: "1000-2000"
                  - name: "Amendment"
                csvMode: "per-component"
                delimiter: ","
                filenameTemplate: "{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv"
                outputRoot: "output"
                batchSize: 100
                backupRetentionDays: 30
                offlineMode: false
                retry:
                  maxAttempts: 3
                  baseDelayMs: 1000
                """;

        String path = writeYaml(tempDir, yaml);
        AppConfig config = ConfigLoader.load(path);

        assertEquals("http://localhost:8080/clm/rest/methods", config.getBaseUrl());
        assertEquals("admin", config.getUsername());
        assertEquals("admin", config.getPassword());
        assertEquals("inputs/endpoints.yml", config.getEndpointsFile());

        assertNotNull(config.getBoTypes());
        assertEquals(2, config.getBoTypes().size());
        assertEquals("Contract", config.getBoTypes().get(0).getName());
        assertEquals("Amendment", config.getBoTypes().get(1).getName());
        assertEquals("1000-2000", config.getTrackingFilter());

        assertEquals("per-component", config.getCsvMode());
        assertEquals(',', config.getDelimiter());
        assertEquals("{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv", config.getFilenameTemplate());
        assertEquals("output", config.getOutputRoot());
        assertEquals(100, config.getBatchSize());
        assertEquals(30, config.getBackupRetentionDays());
        assertFalse(config.isOfflineMode());
        assertEquals(3, config.getRetryMaxAttempts());
        assertEquals(1000L, config.getRetryBaseDelayMs());
    }

    // ------------------------------------------------------------------
    // 2. Defaults applied for optional fields
    // ------------------------------------------------------------------

    @Test
    void testDefaultsApplied() throws IOException {
        String yaml = """
                server:
                  url: "http://example.com"
                  username: "user"
                  password: "pass"
                outputRoot: "out"
                """;

        String path = writeYaml(tempDir, yaml);
        AppConfig config = ConfigLoader.load(path);

        assertEquals("inputs/endpoints.yml", config.getEndpointsFile());
        assertNotNull(config.getBoTypes());
        assertTrue(config.getBoTypes().isEmpty());
        assertEquals("per-component", config.getCsvMode());
        assertEquals(',', config.getDelimiter());
        assertEquals("{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv", config.getFilenameTemplate());
        assertEquals("{BO}_AttachmentsToDownload_{DDMMYYYY}_{HHMMSS}.csv", config.getDownloadsFilenameTemplate());
        assertEquals("MetaData", config.getExportFolderName());
        assertEquals(100, config.getBatchSize());
        assertEquals(30, config.getBackupRetentionDays());
        assertFalse(config.isOfflineMode());
        assertEquals(3, config.getRetryMaxAttempts());
        assertEquals(1000L, config.getRetryBaseDelayMs());
    }

    // ------------------------------------------------------------------
    // 3. Missing server.url throws
    // ------------------------------------------------------------------

    @Test
    void testMissingServerUrlThrows() throws IOException {
        String yaml = """
                server:
                  username: "user"
                  password: "pass"
                outputRoot: "out"
                """;

        String path = writeYaml(tempDir, yaml);

        ConfigValidationException ex = assertThrows(ConfigValidationException.class,
                () -> ConfigLoader.load(path));
        assertTrue(ex.getMessage().contains("server.url"));
    }

    // ------------------------------------------------------------------
    // 4. Missing username throws
    // ------------------------------------------------------------------

    @Test
    void testMissingUsernameThrows() throws IOException {
        String yaml = """
                server:
                  url: "http://example.com"
                  password: "pass"
                outputRoot: "out"
                """;

        String path = writeYaml(tempDir, yaml);

        ConfigValidationException ex = assertThrows(ConfigValidationException.class,
                () -> ConfigLoader.load(path));
        assertTrue(ex.getMessage().contains("server.username"));
    }

    // ------------------------------------------------------------------
    // 5. Missing password throws
    // ------------------------------------------------------------------

    @Test
    void testMissingPasswordThrows() throws IOException {
        String yaml = """
                server:
                  url: "http://example.com"
                  username: "user"
                outputRoot: "out"
                """;

        String path = writeYaml(tempDir, yaml);

        ConfigValidationException ex = assertThrows(ConfigValidationException.class,
                () -> ConfigLoader.load(path));
        assertTrue(ex.getMessage().contains("server.password"));
    }

    // ------------------------------------------------------------------
    // 6. Missing outputRoot throws
    // ------------------------------------------------------------------

    @Test
    void testMissingOutputRootThrows() throws IOException {
        String yaml = """
                server:
                  url: "http://example.com"
                  username: "user"
                  password: "pass"
                """;

        String path = writeYaml(tempDir, yaml);

        ConfigValidationException ex = assertThrows(ConfigValidationException.class,
                () -> ConfigLoader.load(path));
        assertTrue(ex.getMessage().contains("outputRoot"));
    }

    // ------------------------------------------------------------------
    // 7. Invalid csvMode throws
    // ------------------------------------------------------------------

    @Test
    void testInvalidCsvModeThrows() throws IOException {
        String yaml = """
                server:
                  url: "http://example.com"
                  username: "user"
                  password: "pass"
                outputRoot: "out"
                csvMode: "invalid-mode"
                """;

        String path = writeYaml(tempDir, yaml);

        ConfigValidationException ex = assertThrows(ConfigValidationException.class,
                () -> ConfigLoader.load(path));
        assertTrue(ex.getMessage().contains("csvMode"));
    }

    // ------------------------------------------------------------------
    // 8. Invalid batchSize throws
    // ------------------------------------------------------------------

    @Test
    void testInvalidBatchSizeThrows() throws IOException {
        String yaml = """
                server:
                  url: "http://example.com"
                  username: "user"
                  password: "pass"
                outputRoot: "out"
                batchSize: 0
                """;

        String path = writeYaml(tempDir, yaml);

        ConfigValidationException ex = assertThrows(ConfigValidationException.class,
                () -> ConfigLoader.load(path));
        assertTrue(ex.getMessage().contains("batchSize"));
    }

    // ------------------------------------------------------------------
    // 9. Negative backupRetentionDays throws
    // ------------------------------------------------------------------

    @Test
    void testNegativeBackupRetentionThrows() throws IOException {
        String yaml = """
                server:
                  url: "http://example.com"
                  username: "user"
                  password: "pass"
                outputRoot: "out"
                backupRetentionDays: -1
                """;

        String path = writeYaml(tempDir, yaml);

        ConfigValidationException ex = assertThrows(ConfigValidationException.class,
                () -> ConfigLoader.load(path));
        assertTrue(ex.getMessage().contains("backupRetentionDays"));
    }

    // ------------------------------------------------------------------
    // 10. boType with blank name is now allowed (discovery mode signal)
    // ------------------------------------------------------------------

    @Test
    void testBoTypeWithBlankNameAllowed() throws IOException {
        String yaml = """
                server:
                  url: "http://example.com"
                  username: "user"
                  password: "pass"
                outputRoot: "out"
                boTypes:
                  - name:
                """;

        String path = writeYaml(tempDir, yaml);
        // Should NOT throw — blank name is now a valid discovery-mode signal
        AppConfig config = ConfigLoader.load(path);
        assertNotNull(config);
    }

    // ------------------------------------------------------------------
    // 11. Non-existent file throws
    // ------------------------------------------------------------------

    @Test
    void testNonExistentFileThrows() {
        String fakePath = tempDir.resolve("does-not-exist.yml").toString();

        ConfigValidationException ex = assertThrows(ConfigValidationException.class,
                () -> ConfigLoader.load(fakePath));
        assertTrue(ex.getMessage().contains("Cannot read config file"));
    }

    // ------------------------------------------------------------------
    // 12. boUsageTypeFilter: Contract loads cleanly
    // ------------------------------------------------------------------

    @Test
    void testBoUsageTypeFilterContractLoadsCleanly() throws IOException {
        String yaml = """
                server:
                  url: "http://example.com"
                  username: "user"
                  password: "pass"
                outputRoot: "out"
                boUsageTypeFilter: Contract
                """;

        String path = writeYaml(tempDir, yaml);
        AppConfig config = ConfigLoader.load(path);
        assertEquals("Contract", config.getBoUsageTypeFilter());
    }

    // ------------------------------------------------------------------
    // 13. boUsageTypeFilter: Directory loads cleanly
    // ------------------------------------------------------------------

    @Test
    void testBoUsageTypeFilterDirectoryLoadsCleanly() throws IOException {
        String yaml = """
                server:
                  url: "http://example.com"
                  username: "user"
                  password: "pass"
                outputRoot: "out"
                boUsageTypeFilter: Directory
                """;

        String path = writeYaml(tempDir, yaml);
        AppConfig config = ConfigLoader.load(path);
        assertEquals("Directory", config.getBoUsageTypeFilter());
    }

    // ------------------------------------------------------------------
    // 14. boUsageTypeFilter: NonContract loads cleanly
    // ------------------------------------------------------------------

    @Test
    void testBoUsageTypeFilterNonContractLoadsCleanly() throws IOException {
        String yaml = """
                server:
                  url: "http://example.com"
                  username: "user"
                  password: "pass"
                outputRoot: "out"
                boUsageTypeFilter: NonContract
                """;

        String path = writeYaml(tempDir, yaml);
        AppConfig config = ConfigLoader.load(path);
        assertEquals("NonContract", config.getBoUsageTypeFilter());
    }

    // ------------------------------------------------------------------
    // 15. boUsageTypeFilter: contract (lowercase) throws
    // ------------------------------------------------------------------

    @Test
    void testBoUsageTypeFilterLowercaseContractThrows() throws IOException {
        String yaml = """
                server:
                  url: "http://example.com"
                  username: "user"
                  password: "pass"
                outputRoot: "out"
                boUsageTypeFilter: contract
                """;

        String path = writeYaml(tempDir, yaml);

        ConfigValidationException ex = assertThrows(ConfigValidationException.class,
                () -> ConfigLoader.load(path));
        assertTrue(ex.getMessage().contains("Invalid boUsageTypeFilter value"));
    }

    // ------------------------------------------------------------------
    // 16. boUsageTypeFilter: BadValue throws
    // ------------------------------------------------------------------

    @Test
    void testBoUsageTypeFilterBadValueThrows() throws IOException {
        String yaml = """
                server:
                  url: "http://example.com"
                  username: "user"
                  password: "pass"
                outputRoot: "out"
                boUsageTypeFilter: BadValue
                """;

        String path = writeYaml(tempDir, yaml);

        ConfigValidationException ex = assertThrows(ConfigValidationException.class,
                () -> ConfigLoader.load(path));
        assertTrue(ex.getMessage().contains("Invalid boUsageTypeFilter value"));
    }

    // ------------------------------------------------------------------
    // 17. boUsageTypeFilter absent => field is null
    // ------------------------------------------------------------------

    @Test
    void testBoUsageTypeFilterAbsentIsNull() throws IOException {
        String yaml = """
                server:
                  url: "http://example.com"
                  username: "user"
                  password: "pass"
                outputRoot: "out"
                """;

        String path = writeYaml(tempDir, yaml);
        AppConfig config = ConfigLoader.load(path);
        assertNull(config.getBoUsageTypeFilter());
    }
}

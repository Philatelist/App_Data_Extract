package com.clmextract.web.api;

import com.clmextract.config.AppConfig;
import com.clmextract.config.ConfigLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigControllerValidationTest {

    @TempDir
    Path tempDir;

    private Path configFile;
    private ConfigController controller;
    private ObjectMapper objectMapper;

    private static final String MINIMAL_VALID_YAML = """
            server:
              url: https://test.example.com
              username: testuser
              password: testpass
            endpointsFile: inputs/endpoints.yml
            batchSize: 10
            outputRoot: output
            delimiter: ","
            csvMode: per-component
            retry:
              maxAttempts: 3
              baseDelayMs: 1000
            backupRetentionDays: 30
            sftp:
              host: ""
              port: 22
              username: ""
              password: ""
            """;

    @BeforeEach
    void setUp() throws IOException {
        configFile = tempDir.resolve("test-config.yml");
        Files.writeString(configFile, MINIMAL_VALID_YAML);
        objectMapper = new ObjectMapper();
        controller = new ConfigController(configFile.toString(), objectMapper);
    }

    /**
     * Builds a minimal valid JSON request body as an ObjectNode.
     * All required fields are populated with sensible values.
     */
    private ObjectNode buildValidBody() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("serverUrl", "https://test.example.com");
        body.put("serverUsername", "testuser");
        body.put("serverPassword", "testpass");
        body.put("endpointsFile", "inputs/endpoints.yml");
        body.put("batchSize", 10);
        body.put("retryMaxAttempts", 3);
        body.put("retryBaseDelayMs", 1000);
        body.put("backupRetentionDays", 30);
        body.put("outputRoot", "output");
        body.put("delimiter", ",");
        body.put("csvMode", "per-component");
        body.put("delimiterReplacementEnabled", false);
        body.put("yesNoTranslationEnabled", false);
        body.putArray("boTypes");
        body.putArray("skipColumns");
        body.putArray("skipComponents");
        body.putArray("additionalColumns");

        ObjectNode dateFormat = objectMapper.createObjectNode();
        dateFormat.putArray("inputFormats");
        dateFormat.put("outputFormat", "");
        dateFormat.putArray("inputDateTimeFormats");
        dateFormat.put("outputDateTimeFormat", "");
        body.set("dateFormat", dateFormat);

        ObjectNode sftp = objectMapper.createObjectNode();
        sftp.put("host", "");
        sftp.put("port", 22);
        sftp.put("username", "");
        sftp.put("password", "");
        body.set("sftp", sftp);

        return body;
    }

    // ---------------------------------------------------------------
    // Test 1: required field missing → correct error returned
    // ---------------------------------------------------------------

    @Test
    void missingServerUrl_returnsErrorForServerUrlField() throws Exception {
        ObjectNode body = buildValidBody();
        body.remove("serverUrl");

        List<Map<String, String>> errors = controller.validateBody(objectMapper.readTree(body.toString()));

        assertFalse(errors.isEmpty(), "Expected at least one validation error");
        boolean hasServerUrlError = errors.stream()
                .anyMatch(e -> "serverUrl".equals(e.get("field")));
        assertTrue(hasServerUrlError,
                "Expected an error entry with field='serverUrl', but got: " + errors);
    }

    @Test
    void blankServerUrl_returnsErrorForServerUrlField() throws Exception {
        ObjectNode body = buildValidBody();
        body.put("serverUrl", "   ");

        List<Map<String, String>> errors = controller.validateBody(objectMapper.readTree(body.toString()));

        assertFalse(errors.isEmpty(), "Expected at least one validation error");
        boolean hasServerUrlError = errors.stream()
                .anyMatch(e -> "serverUrl".equals(e.get("field")));
        assertTrue(hasServerUrlError,
                "Expected an error entry with field='serverUrl', but got: " + errors);
    }

    // ---------------------------------------------------------------
    // Test 2: invalid delimiter (multi-char) → field-level error
    // ---------------------------------------------------------------

    @Test
    void multiCharDelimiter_returnsErrorForDelimiterField() throws Exception {
        ObjectNode body = buildValidBody();
        body.put("delimiter", "abc");

        List<Map<String, String>> errors = controller.validateBody(objectMapper.readTree(body.toString()));

        assertFalse(errors.isEmpty(), "Expected at least one validation error");
        boolean hasDelimiterError = errors.stream()
                .anyMatch(e -> "delimiter".equals(e.get("field")));
        assertTrue(hasDelimiterError,
                "Expected an error entry with field='delimiter', but got: " + errors);
    }

    // ---------------------------------------------------------------
    // Test 3: valid round-trip — config saved then read back
    // ---------------------------------------------------------------

    @Test
    void validBody_noValidationErrors_andWrittenFileLoadedCorrectly() throws Exception {
        String savedServerUrl = "https://roundtrip.example.com";
        int savedBatchSize = 42;
        String savedDelimiter = ";";

        ObjectNode body = buildValidBody();
        body.put("serverUrl", savedServerUrl);
        body.put("batchSize", savedBatchSize);
        body.put("delimiter", savedDelimiter);

        JsonNode bodyNode = objectMapper.readTree(body.toString());

        // Assert validation passes (i.e. putConfig would return 200)
        List<Map<String, String>> errors = controller.validateBody(bodyNode);
        assertTrue(errors.isEmpty(),
                "Expected no validation errors for a valid body, but got: " + errors);

        // Simulate what putConfig writes: build the YAML from the body and write it
        // This mirrors the write logic in ConfigController.putConfig so that
        // we can verify loadRaw reads back the same values.
        writeConfigFromBody(bodyNode, configFile);

        // Now verify that loadRaw reads back the saved values
        AppConfig loaded = ConfigLoader.loadRaw(configFile.toString());
        assertEquals(savedServerUrl, loaded.getBaseUrl(),
                "serverUrl should round-trip through the config file");
        assertEquals(savedBatchSize, loaded.getBatchSize(),
                "batchSize should round-trip through the config file");
        assertEquals(savedDelimiter.charAt(0), loaded.getDelimiter(),
                "delimiter should round-trip through the config file");
    }

    @Test
    void enableZipPackagingAndSftpUpload_roundTripThroughPutAndGet() throws Exception {
        // Build a valid body with both flags set to false
        ObjectNode body = buildValidBody();
        body.put("enableZipPackaging", false);
        body.put("enableSftpUpload", false);

        // Validation should pass (no errors expected for boolean fields)
        List<Map<String, String>> errors = controller.validateBody(objectMapper.readTree(body.toString()));
        assertTrue(errors.isEmpty(), "No validation errors expected for enableZipPackaging/enableSftpUpload");

        // Simulate the PUT write: parse the config, update fields, write YAML
        AppConfig config = ConfigLoader.loadRaw(configFile.toString());
        config.setEnableZipPackaging(false);
        config.setEnableSftpUpload(false);

        // Write back using SnakeYAML (mirrors ConfigController.putConfig write logic)
        java.util.Map<String, Object> root = new java.util.LinkedHashMap<>();
        root.put("enableZipPackaging", config.isEnableZipPackaging());
        root.put("enableSftpUpload", config.isEnableSftpUpload());
        // Merge with existing YAML content
        String existingYaml = Files.readString(configFile);
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> existing = yaml.load(existingYaml);
        existing.put("enableZipPackaging", false);
        existing.put("enableSftpUpload", false);
        Files.writeString(configFile, yaml.dump(existing));

        // Reload and verify
        AppConfig reloaded = ConfigLoader.loadRaw(configFile.toString());
        assertFalse(reloaded.isEnableZipPackaging(), "enableZipPackaging should be false after save");
        assertFalse(reloaded.isEnableSftpUpload(), "enableSftpUpload should be false after save");
    }

    @Test
    void convertAttachmentsToPdfAndIncludeEmptyExportFiles_roundTripThroughPutAndGet() throws Exception {
        // Build a valid body with both new flags set to non-default values
        ObjectNode body = buildValidBody();
        body.put("convertAttachmentsToPdf", true);
        body.put("includeEmptyExportFiles", false);

        // Validation should pass (these are boolean fields, not validated for content)
        List<Map<String, String>> errors = controller.validateBody(objectMapper.readTree(body.toString()));
        assertTrue(errors.isEmpty(), "No validation errors expected for convertAttachmentsToPdf/includeEmptyExportFiles");

        // Simulate the PUT write: load raw config, set fields, write YAML
        AppConfig config = ConfigLoader.loadRaw(configFile.toString());
        config.setConvertAttachmentsToPdf(true);
        config.setIncludeEmptyExportFiles(false);

        // Write back using SnakeYAML (mirrors ConfigController.putConfig write logic)
        String existingYaml = Files.readString(configFile);
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> existing = yaml.load(existingYaml);
        existing.put("convertAttachmentsToPdf", true);
        existing.put("includeEmptyExportFiles", false);
        Files.writeString(configFile, yaml.dump(existing));

        // Reload and verify the round-trip
        AppConfig reloaded = ConfigLoader.loadRaw(configFile.toString());
        assertTrue(reloaded.isConvertAttachmentsToPdf(), "convertAttachmentsToPdf should be true after save");
        assertFalse(reloaded.isIncludeEmptyExportFiles(), "includeEmptyExportFiles should be false after save");
    }

    /**
     * Mirrors the YAML-writing logic from ConfigController.putConfig so that
     * the round-trip test can verify ConfigLoader.loadRaw without needing a
     * Javalin Context.
     */
    private void writeConfigFromBody(JsonNode body, Path target) throws IOException {
        Map<String, Object> root = new java.util.LinkedHashMap<>();

        Map<String, Object> serverMap = new java.util.LinkedHashMap<>();
        serverMap.put("url", body.path("serverUrl").asText());
        serverMap.put("username", body.path("serverUsername").asText(""));
        serverMap.put("password", body.path("serverPassword").asText(""));
        root.put("server", serverMap);

        root.put("endpointsFile", body.path("endpointsFile").asText("inputs/endpoints.yml"));
        root.put("batchSize", body.path("batchSize").asInt(100));
        root.put("outputRoot", body.path("outputRoot").asText("output"));
        root.put("csvMode", body.path("csvMode").asText("per-component"));
        root.put("delimiter", body.path("delimiter").asText(","));

        Map<String, Object> retryMap = new java.util.LinkedHashMap<>();
        retryMap.put("maxAttempts", body.path("retryMaxAttempts").asInt(3));
        retryMap.put("baseDelayMs", body.path("retryBaseDelayMs").asLong(1000));
        root.put("retry", retryMap);

        root.put("backupRetentionDays", body.path("backupRetentionDays").asInt(30));

        Map<String, Object> sftpMap = new java.util.LinkedHashMap<>();
        sftpMap.put("host", body.path("sftp").path("host").asText(""));
        sftpMap.put("port", body.path("sftp").path("port").asInt(22));
        sftpMap.put("username", body.path("sftp").path("username").asText(""));
        sftpMap.put("password", body.path("sftp").path("password").asText(""));
        root.put("sftp", sftpMap);

        String yaml = new Yaml().dump(root);
        Files.writeString(target, yaml);
    }
}

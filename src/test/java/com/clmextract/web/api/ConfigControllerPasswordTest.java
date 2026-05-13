package com.clmextract.web.api;

import com.clmextract.config.CredentialEncryptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for password masking in getConfig() and preserve-on-blank in putConfig().
 */
class ConfigControllerPasswordTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // -----------------------------------------------------------------------
    // Minimal Context stub
    // -----------------------------------------------------------------------

    /**
     * A minimal stub of io.javalin.http.Context that records the string result
     * written to the response and supports sessionAttribute reads and body reads.
     * All unused abstract methods throw UnsupportedOperationException.
     */
    private static class StubContext implements Context {

        private final Map<String, Object> sessionAttributes = new HashMap<>();
        private String capturedResult;
        private String requestBody = "";

        void setSessionAttribute(String key, Object value) {
            sessionAttributes.put(key, value);
        }

        void setRequestBody(String body) {
            this.requestBody = body;
        }

        String getCapturedResult() {
            return capturedResult;
        }

        // ---- Methods actually called by ConfigController ----

        @Override
        @SuppressWarnings("unchecked")
        public <T> T sessionAttribute(String key) {
            return (T) sessionAttributes.get(key);
        }

        @Override
        public Context result(InputStream stream) {
            try {
                capturedResult = new String(stream.readAllBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return this;
        }

        @Override
        public Context result(String result) {
            capturedResult = result;
            return this;
        }

        @Override
        public InputStream resultInputStream() {
            if (capturedResult == null) return null;
            return new ByteArrayInputStream(capturedResult.getBytes());
        }

        @Override
        public Context status(int statusCode) { return this; }

        @Override
        public Context contentType(String contentType) { return this; }

        @Override
        public String body() { return requestBody; }

        // ---- Unused abstract methods — stub implementations ----

        @Override
        public jakarta.servlet.http.HttpServletRequest req() {
            throw new UnsupportedOperationException();
        }

        @Override
        public jakarta.servlet.http.HttpServletResponse res() {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.javalin.http.HandlerType handlerType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String matchedPath() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String endpointHandlerPath() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T appData(io.javalin.config.Key<T> key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.javalin.json.JsonMapper jsonMapper() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T with(Class<? extends io.javalin.plugin.ContextPlugin<?, T>> pluginClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean strictContentTypes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String pathParam(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Map<String, String> pathParamMap() {
            throw new UnsupportedOperationException();
        }

        @Override
        public jakarta.servlet.ServletOutputStream outputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Context minSizeForCompression(int minSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void future(java.util.function.Supplier<? extends java.util.concurrent.CompletableFuture<?>> supplier) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void redirect(String location, io.javalin.http.HttpStatus status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeJsonStream(java.util.stream.Stream<?> stream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Context skipRemainingHandlers() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Set<io.javalin.security.RouteRole> routeRoles() {
            throw new UnsupportedOperationException();
        }
    }

    // -----------------------------------------------------------------------
    // YAML helpers
    // -----------------------------------------------------------------------

    private Path writeConfig(String yaml) throws IOException {
        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, yaml);
        return file;
    }

    private static final String MINIMAL_YAML_TEMPLATE =
            "server:\n" +
            "  url: https://test.example.com\n" +
            "  username: testuser\n" +
            "  password: '%s'\n" +
            "endpointsFile: inputs/endpoints.yml\n" +
            "batchSize: 10\n" +
            "outputRoot: output\n" +
            "delimiter: ','\n" +
            "csvMode: per-component\n" +
            "retry:\n" +
            "  maxAttempts: 3\n" +
            "  baseDelayMs: 1000\n" +
            "backupRetentionDays: 30\n" +
            "sftp:\n" +
            "  host: ''\n" +
            "  port: 22\n" +
            "  username: ''\n" +
            "  password: ''\n";

    private ObjectNode buildValidPutBody() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("serverUrl", "https://test.example.com");
        body.put("serverUsername", "testuser");
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
        body.putArray("adminEmails");

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

    // -----------------------------------------------------------------------
    // Task 3, Test 1 — getConfig with real password → serverPasswordIsSet true
    // -----------------------------------------------------------------------

    @Test
    void getConfig_withSetPassword_serverPasswordIsBlankAndIsSetIsTrue() throws Exception {
        Path config = writeConfig(String.format(MINIMAL_YAML_TEMPLATE, "somePassword"));
        ConfigController controller = new ConfigController(config.toString(), objectMapper);

        StubContext ctx = new StubContext();
        ctx.setSessionAttribute("role", "ADMIN");

        controller.getConfig(ctx);

        assertNotNull(ctx.getCapturedResult(), "Expected a JSON response");
        JsonNode resp = objectMapper.readTree(ctx.getCapturedResult());

        assertEquals("", resp.get("serverPassword").asText(),
                "serverPassword must be empty string in response");
        assertTrue(resp.get("serverPasswordIsSet").asBoolean(),
                "serverPasswordIsSet must be true when password is 'somePassword'");
    }

    // -----------------------------------------------------------------------
    // Task 3, Test 2 — getConfig with blank password → serverPasswordIsSet false
    // -----------------------------------------------------------------------

    @Test
    void getConfig_withBlankPassword_serverPasswordIsSetIsFalse() throws Exception {
        Path config = writeConfig(String.format(MINIMAL_YAML_TEMPLATE, ""));
        ConfigController controller = new ConfigController(config.toString(), objectMapper);

        StubContext ctx = new StubContext();
        ctx.setSessionAttribute("role", "ADMIN");

        controller.getConfig(ctx);

        assertNotNull(ctx.getCapturedResult(), "Expected a JSON response");
        JsonNode resp = objectMapper.readTree(ctx.getCapturedResult());

        assertEquals("", resp.get("serverPassword").asText(),
                "serverPassword must be empty string in response");
        assertFalse(resp.get("serverPasswordIsSet").asBoolean(),
                "serverPasswordIsSet must be false when password is blank");
    }

    // -----------------------------------------------------------------------
    // Task 3, Test 3 — getConfig with ENC(...) token → serverPasswordIsSet true
    // -----------------------------------------------------------------------

    @Test
    void getConfig_withEncToken_serverPasswordIsSetIsTrue() throws Exception {
        Path config = writeConfig(String.format(MINIMAL_YAML_TEMPLATE, "ENC(abc123)"));
        ConfigController controller = new ConfigController(config.toString(), objectMapper);

        StubContext ctx = new StubContext();
        ctx.setSessionAttribute("role", "ADMIN");

        controller.getConfig(ctx);

        assertNotNull(ctx.getCapturedResult(), "Expected a JSON response");
        JsonNode resp = objectMapper.readTree(ctx.getCapturedResult());

        assertEquals("", resp.get("serverPassword").asText(),
                "serverPassword must be empty string in response even for ENC token");
        assertTrue(resp.get("serverPasswordIsSet").asBoolean(),
                "serverPasswordIsSet must be true when raw value is ENC(abc123)");
    }

    // -----------------------------------------------------------------------
    // Task 3, Test 4 — putConfig with blank password → original password preserved
    // -----------------------------------------------------------------------

    @Test
    void putConfig_withBlankPassword_preservesOriginalPasswordInYaml() throws Exception {
        String originalPassword = "originalSecret";
        Path configFile = writeConfig(String.format(MINIMAL_YAML_TEMPLATE, originalPassword));
        ConfigController controller = new ConfigController(configFile.toString(), objectMapper);

        ObjectNode body = buildValidPutBody();
        // serverPassword is explicitly blank — should not overwrite the existing one
        body.put("serverPassword", "");

        StubContext ctx = new StubContext();
        ctx.setSessionAttribute("role", "ADMIN");
        ctx.setRequestBody(objectMapper.writeValueAsString(body));

        controller.putConfig(ctx);

        // Read the YAML that was written back
        String writtenYaml = Files.readString(configFile);
        @SuppressWarnings("unchecked")
        Map<String, Object> root = new Yaml().load(writtenYaml);
        @SuppressWarnings("unchecked")
        Map<String, Object> serverSection = (Map<String, Object>) root.get("server");

        assertEquals(originalPassword, serverSection.get("password"),
                "putConfig must preserve the existing password when incoming value is blank");
    }

    // -----------------------------------------------------------------------
    // Task 3, Test 5 — putConfig with non-blank password → new password written
    // -----------------------------------------------------------------------

    @Test
    void putConfig_withNonBlankPassword_writesNewPasswordToYaml() throws Exception {
        String originalPassword = "oldPassword";
        String newPassword = "newPassword";
        Path configFile = writeConfig(String.format(MINIMAL_YAML_TEMPLATE, originalPassword));
        ConfigController controller = new ConfigController(configFile.toString(), objectMapper);

        ObjectNode body = buildValidPutBody();
        body.put("serverPassword", newPassword);

        StubContext ctx = new StubContext();
        ctx.setSessionAttribute("role", "ADMIN");
        ctx.setRequestBody(objectMapper.writeValueAsString(body));

        controller.putConfig(ctx);

        String writtenYaml = Files.readString(configFile);
        @SuppressWarnings("unchecked")
        Map<String, Object> root = new Yaml().load(writtenYaml);
        @SuppressWarnings("unchecked")
        Map<String, Object> serverSection = (Map<String, Object>) root.get("server");

        String storedPassword = (String) serverSection.get("password");
        if (CredentialEncryptor.getMasterKey().isPresent()) {
            assertTrue(storedPassword.startsWith("ENC(") && storedPassword.endsWith(")"),
                    "putConfig must store an ENC(...) token when CLM_EXTRACT_KEY is set");
        } else {
            assertEquals(newPassword, storedPassword,
                    "putConfig must write the plaintext password when CLM_EXTRACT_KEY is absent");
        }
    }
}

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
 * Tests for Slice 4 — Encrypted Password Storage in ConfigController.putConfig().
 */
class ConfigControllerEncryptionTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // -----------------------------------------------------------------------
    // Minimal Context stub (same pattern as ConfigControllerPasswordTest)
    // -----------------------------------------------------------------------

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
    // YAML / request body helpers
    // -----------------------------------------------------------------------

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

    private Path writeConfig(String yaml) throws IOException {
        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, yaml);
        return file;
    }

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
    // Test 1 — Non-blank password submitted: stored value is ENC(...) or plaintext
    // -----------------------------------------------------------------------

    @Test
    void putConfig_nonBlankPassword_storedAsEncryptedOrPlaintext() throws Exception {
        String submittedPassword = "mySecretPassword";
        Path configFile = writeConfig(String.format(MINIMAL_YAML_TEMPLATE, "oldPassword"));
        ConfigController controller = new ConfigController(configFile.toString(), objectMapper);

        ObjectNode body = buildValidPutBody();
        body.put("serverPassword", submittedPassword);

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

        // The stored value must be either ENC(...) (key present) or the original plaintext (no key)
        assertTrue(
            storedPassword.startsWith("ENC(") || storedPassword.equals(submittedPassword),
            "Stored password must be ENC(...) when key is set, or original plaintext when key is absent; got: " + storedPassword
        );

        // Validate consistency with actual env var state
        if (CredentialEncryptor.getMasterKey().isPresent()) {
            assertTrue(storedPassword.startsWith("ENC(") && storedPassword.endsWith(")"),
                    "CLM_EXTRACT_KEY is set — stored value must be ENC(...)");
        } else {
            assertEquals(submittedPassword, storedPassword,
                    "CLM_EXTRACT_KEY is absent — stored value must be plaintext");
        }
    }

    // -----------------------------------------------------------------------
    // Test 2 — Blank password submitted: original password is preserved (regression guard)
    // -----------------------------------------------------------------------

    @Test
    void putConfig_blankPassword_preservesOriginalPassword() throws Exception {
        String originalPassword = "existingSecret";
        Path configFile = writeConfig(String.format(MINIMAL_YAML_TEMPLATE, originalPassword));
        ConfigController controller = new ConfigController(configFile.toString(), objectMapper);

        ObjectNode body = buildValidPutBody();
        body.put("serverPassword", "");  // blank — must not overwrite

        StubContext ctx = new StubContext();
        ctx.setSessionAttribute("role", "ADMIN");
        ctx.setRequestBody(objectMapper.writeValueAsString(body));

        controller.putConfig(ctx);

        String writtenYaml = Files.readString(configFile);
        @SuppressWarnings("unchecked")
        Map<String, Object> root = new Yaml().load(writtenYaml);
        @SuppressWarnings("unchecked")
        Map<String, Object> serverSection = (Map<String, Object>) root.get("server");

        assertEquals(originalPassword, serverSection.get("password"),
                "putConfig must preserve the existing password when incoming value is blank");
    }

    // -----------------------------------------------------------------------
    // Test 3 — getConfig() after an encrypting save: serverPasswordIsSet true, serverPassword blank
    // -----------------------------------------------------------------------

    @Test
    void getConfig_afterEncryptingSave_passwordIsSetAndNotExposed() throws Exception {
        String submittedPassword = "anotherSecret";
        Path configFile = writeConfig(String.format(MINIMAL_YAML_TEMPLATE, "oldPassword"));
        ConfigController controller = new ConfigController(configFile.toString(), objectMapper);

        // First: PUT with a non-blank password so it gets saved (encrypted or plain)
        ObjectNode putBody = buildValidPutBody();
        putBody.put("serverPassword", submittedPassword);

        StubContext putCtx = new StubContext();
        putCtx.setSessionAttribute("role", "ADMIN");
        putCtx.setRequestBody(objectMapper.writeValueAsString(putBody));
        controller.putConfig(putCtx);

        // Now: GET and verify the response shape
        StubContext getCtx = new StubContext();
        getCtx.setSessionAttribute("role", "ADMIN");
        controller.getConfig(getCtx);

        assertNotNull(getCtx.getCapturedResult(), "Expected a JSON response from getConfig");
        JsonNode resp = objectMapper.readTree(getCtx.getCapturedResult());

        assertTrue(resp.get("serverPasswordIsSet").asBoolean(),
                "serverPasswordIsSet must be true after saving a non-blank password");
        assertEquals("", resp.get("serverPassword").asText(),
                "serverPassword must always be empty string in the getConfig response");
    }
}

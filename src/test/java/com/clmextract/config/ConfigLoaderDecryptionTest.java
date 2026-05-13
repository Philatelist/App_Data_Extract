package com.clmextract.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderDecryptionTest {

    @TempDir
    Path tempDir;

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private String writeYaml(String content) throws IOException {
        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, content);
        return file.toString();
    }

    private AppConfig minimalConfig(String password) {
        AppConfig config = new AppConfig();
        config.setBaseUrl("http://example.com");
        config.setUsername("user");
        config.setPassword(password);
        config.setOutputRoot("out");
        return config;
    }

    // ------------------------------------------------------------------
    // 1. ENC password + key set → password is decrypted to original plaintext
    // ------------------------------------------------------------------

    @Test
    void resolvePasswords_encServerPassword_withKey_decryptsToPlaintext() {
        String plaintext = "mySecret";
        String masterKey = "testKey";
        String encrypted = CredentialEncryptor.encrypt(plaintext, masterKey);

        AppConfig config = minimalConfig(encrypted);
        ConfigLoader.resolvePasswords(config, Optional.of(masterKey));

        assertEquals(plaintext, config.getPassword());
    }

    // ------------------------------------------------------------------
    // 2. ENC password + no key → throws ConfigValidationException
    // ------------------------------------------------------------------

    @Test
    void resolvePasswords_encServerPassword_noKey_throwsWithMessage() {
        String encrypted = CredentialEncryptor.encrypt("mySecret", "testKey");

        AppConfig config = minimalConfig(encrypted);
        ConfigValidationException ex = assertThrows(ConfigValidationException.class,
                () -> ConfigLoader.resolvePasswords(config, Optional.empty()));

        assertTrue(ex.getMessage().contains("CLM_EXTRACT_KEY"),
                "Exception message should mention CLM_EXTRACT_KEY but was: " + ex.getMessage());
    }

    // ------------------------------------------------------------------
    // 3. Plaintext password + key present → no exception, password unchanged
    // ------------------------------------------------------------------

    @Test
    void resolvePasswords_plaintextPassword_withKey_noExceptionPasswordUnchanged() {
        AppConfig config = minimalConfig("plainPassword");

        assertDoesNotThrow(() ->
                ConfigLoader.resolvePasswords(config, Optional.of("anyKey")));

        assertEquals("plainPassword", config.getPassword());
    }

    // ------------------------------------------------------------------
    // 4. Plaintext password + no key → no exception, password unchanged
    // ------------------------------------------------------------------

    @Test
    void resolvePasswords_plaintextPassword_noKey_noExceptionPasswordUnchanged() {
        AppConfig config = minimalConfig("plainPassword");

        assertDoesNotThrow(() ->
                ConfigLoader.resolvePasswords(config, Optional.empty()));

        assertEquals("plainPassword", config.getPassword());
    }

    // ------------------------------------------------------------------
    // 5. ENC sftp.password + key set → sftp password decrypted
    // ------------------------------------------------------------------

    @Test
    void resolvePasswords_encSftpPassword_withKey_decryptsToPlaintext() {
        String plaintext = "sftpSecret";
        String masterKey = "testKey";
        String encrypted = CredentialEncryptor.encrypt(plaintext, masterKey);

        AppConfig config = minimalConfig("serverPass");
        AppConfig.SftpConfig sftp = new AppConfig.SftpConfig();
        sftp.setPassword(encrypted);
        config.setSftp(sftp);

        ConfigLoader.resolvePasswords(config, Optional.of(masterKey));

        assertEquals(plaintext, config.getSftp().getPassword());
    }

    // ------------------------------------------------------------------
    // 6. ENC sftp.password + no key → throws ConfigValidationException
    // ------------------------------------------------------------------

    @Test
    void resolvePasswords_encSftpPassword_noKey_throws() {
        String encrypted = CredentialEncryptor.encrypt("sftpSecret", "testKey");

        AppConfig config = minimalConfig("serverPass");
        AppConfig.SftpConfig sftp = new AppConfig.SftpConfig();
        sftp.setPassword(encrypted);
        config.setSftp(sftp);

        ConfigValidationException ex = assertThrows(ConfigValidationException.class,
                () -> ConfigLoader.resolvePasswords(config, Optional.empty()));

        assertTrue(ex.getMessage().contains("CLM_EXTRACT_KEY"),
                "Exception message should mention CLM_EXTRACT_KEY but was: " + ex.getMessage());
    }

    // ------------------------------------------------------------------
    // 7. load() with valid plaintext config (no ENC, no env key) → backward compat
    // ------------------------------------------------------------------

    @Test
    void load_plaintextConfig_noEnvKey_loadsSuccessfully() throws IOException {
        String yaml = """
                server:
                  url: "http://example.com"
                  username: "user"
                  password: "pass"
                outputRoot: "out"
                """;

        String path = writeYaml(yaml);
        // CLM_EXTRACT_KEY is not set in test environment (or if it is, plaintext
        // path still succeeds — it only warns).
        AppConfig config = ConfigLoader.load(path);

        assertEquals("http://example.com", config.getBaseUrl());
        assertEquals("user", config.getUsername());
        assertEquals("pass", config.getPassword());
    }

    // ------------------------------------------------------------------
    // 8. null password field → resolvePasswords does not throw
    // ------------------------------------------------------------------

    @Test
    void resolvePasswords_nullPassword_noException() {
        AppConfig config = minimalConfig(null);

        assertDoesNotThrow(() ->
                ConfigLoader.resolvePasswords(config, Optional.of("anyKey")));
    }
}

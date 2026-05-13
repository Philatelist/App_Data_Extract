package com.clmextract.web;

import com.clmextract.config.AppConfig;
import com.clmextract.config.CredentialEncryptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link WebServer#checkEncryptedCredentials(AppConfig)}.
 *
 * All tests that verify the "no key" path are only meaningful when
 * CLM_EXTRACT_KEY is absent from the environment, so they are guarded with
 * {@code @DisabledIfEnvironmentVariable}.
 */
class WebServerStartupTest {

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Builds a minimal AppConfig with the given server password. */
    private static AppConfig configWithServerPassword(String password) {
        AppConfig config = new AppConfig();
        config.setPassword(password);
        return config;
    }

    /** Builds a minimal AppConfig with the given SFTP password. */
    private static AppConfig configWithSftpPassword(String sftpPassword) {
        AppConfig config = new AppConfig();
        config.setPassword("plaintext-server-pass");
        AppConfig.SftpConfig sftp = new AppConfig.SftpConfig();
        sftp.setPassword(sftpPassword);
        config.setSftp(sftp);
        return config;
    }

    // ------------------------------------------------------------------
    // Test 1: ENC(...) server password + no key -> IllegalStateException
    // ------------------------------------------------------------------

    @Test
    @DisabledIfEnvironmentVariable(named = "CLM_EXTRACT_KEY", matches = ".*")
    void encryptedServerPassword_noKey_throwsIllegalStateException() {
        AppConfig config = configWithServerPassword("ENC(someBase64Blob)");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> WebServer.checkEncryptedCredentials(config));

        assertTrue(ex.getMessage().contains("CLM_EXTRACT_KEY"),
                "Exception message should mention CLM_EXTRACT_KEY but was: " + ex.getMessage());
    }

    // ------------------------------------------------------------------
    // Test 2: ENC(...) SFTP password + no key -> IllegalStateException
    // ------------------------------------------------------------------

    @Test
    @DisabledIfEnvironmentVariable(named = "CLM_EXTRACT_KEY", matches = ".*")
    void encryptedSftpPassword_noKey_throwsIllegalStateException() {
        AppConfig config = configWithSftpPassword("ENC(anotherBase64Blob)");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> WebServer.checkEncryptedCredentials(config));

        assertTrue(ex.getMessage().contains("CLM_EXTRACT_KEY"),
                "Exception message should mention CLM_EXTRACT_KEY but was: " + ex.getMessage());
    }

    // ------------------------------------------------------------------
    // Test 3: Plaintext passwords + no key -> no exception
    // ------------------------------------------------------------------

    @Test
    @DisabledIfEnvironmentVariable(named = "CLM_EXTRACT_KEY", matches = ".*")
    void plaintextPasswords_noKey_noException() {
        AppConfig config = configWithServerPassword("plaintext-password");

        assertDoesNotThrow(() -> WebServer.checkEncryptedCredentials(config));
    }

    // ------------------------------------------------------------------
    // Test 4: ENC(...) server password + key present -> no exception
    //
    // We cannot set an environment variable from a test, so this test is
    // only meaningful when CLM_EXTRACT_KEY is already present in the
    // environment. The test is skipped (via assumeTrue) if the key is
    // absent so the suite still passes in CI without the variable.
    // ------------------------------------------------------------------

    @Test
    void encryptedServerPassword_keyPresent_noException() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                CredentialEncryptor.getMasterKey().isPresent(),
                "Skipping: CLM_EXTRACT_KEY is not set, so the key-present branch cannot be tested");

        AppConfig config = configWithServerPassword("ENC(someBase64Blob)");

        // When the key is present, getMasterKey() returns non-empty and the
        // method must NOT throw, regardless of whether decryption would succeed.
        assertDoesNotThrow(() -> WebServer.checkEncryptedCredentials(config));
    }
}

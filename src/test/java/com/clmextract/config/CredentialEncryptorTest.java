package com.clmextract.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialEncryptorTest {

    // ------------------------------------------------------------------
    // 1. Encrypt / decrypt round-trip
    // ------------------------------------------------------------------

    @Test
    void encryptDecryptRoundTrip_returnsOriginalPlaintext() {
        String masterKey = "my-secret-master-key";
        String plaintext = "myS3cretP@ssword!";

        String encrypted = CredentialEncryptor.encrypt(plaintext, masterKey);
        String decrypted = CredentialEncryptor.decrypt(encrypted, masterKey);

        assertEquals(plaintext, decrypted);
    }

    @Test
    void encryptDecryptRoundTrip_encryptedValueIsMarkedAsEncrypted() {
        String encrypted = CredentialEncryptor.encrypt("hello", "key123");
        assertTrue(CredentialEncryptor.isEncrypted(encrypted));
    }

    @Test
    void encryptDecryptRoundTrip_differentInvocationsProduceDifferentCiphertext() {
        String plaintext = "same plaintext";
        String key = "same-key";
        String enc1 = CredentialEncryptor.encrypt(plaintext, key);
        String enc2 = CredentialEncryptor.encrypt(plaintext, key);

        // Random IV means ciphertext must differ each time
        assertFalse(enc1.equals(enc2), "Two encryptions of the same plaintext should differ (random IV)");
        // But both must decrypt back to the same value
        assertEquals(plaintext, CredentialEncryptor.decrypt(enc1, key));
        assertEquals(plaintext, CredentialEncryptor.decrypt(enc2, key));
    }

    // ------------------------------------------------------------------
    // 2. isEncrypted
    // ------------------------------------------------------------------

    @Test
    void isEncrypted_encWrappedValue_returnsTrue() {
        assertTrue(CredentialEncryptor.isEncrypted("ENC(abc)"));
    }

    @Test
    void isEncrypted_plaintext_returnsFalse() {
        assertFalse(CredentialEncryptor.isEncrypted("plaintext"));
    }

    @Test
    void isEncrypted_null_returnsFalse() {
        assertFalse(CredentialEncryptor.isEncrypted(null));
    }

    @Test
    void isEncrypted_onlyPrefix_returnsFalse() {
        assertFalse(CredentialEncryptor.isEncrypted("ENC("));
    }

    @Test
    void isEncrypted_onlySuffix_returnsFalse() {
        assertFalse(CredentialEncryptor.isEncrypted(")"));
    }

    @Test
    void isEncrypted_emptyString_returnsFalse() {
        assertFalse(CredentialEncryptor.isEncrypted(""));
    }

    // ------------------------------------------------------------------
    // 3. Tampered ciphertext throws ConfigValidationException
    // ------------------------------------------------------------------

    @Test
    void decrypt_tamperedCiphertext_throwsConfigValidationException() {
        String masterKey = "test-master-key";
        String encrypted = CredentialEncryptor.encrypt("sensitive-data", masterKey);

        // Strip the ENC(...) wrapper, decode, flip a byte, re-encode, re-wrap
        String inner = encrypted.substring("ENC(".length(), encrypted.length() - 1);
        byte[] blob = Base64.getDecoder().decode(inner);
        // Flip a byte in the ciphertext portion (after the 12-byte IV)
        blob[12] = (byte) (blob[12] ^ 0xFF);
        String tampered = "ENC(" + Base64.getEncoder().encodeToString(blob) + ")";

        ConfigValidationException ex = assertThrows(ConfigValidationException.class,
                () -> CredentialEncryptor.decrypt(tampered, masterKey));
        assertTrue(ex.getMessage().contains("Failed to decrypt credential"));
    }

    @Test
    void decrypt_wrongKey_throwsConfigValidationException() {
        String encrypted = CredentialEncryptor.encrypt("secret", "correct-key");

        ConfigValidationException ex = assertThrows(ConfigValidationException.class,
                () -> CredentialEncryptor.decrypt(encrypted, "wrong-key"));
        assertTrue(ex.getMessage().contains("Failed to decrypt credential"));
    }

    // ------------------------------------------------------------------
    // 4. getMasterKey returns empty when env var is absent
    // ------------------------------------------------------------------

    @Test
    @DisabledIfEnvironmentVariable(named = "CLM_EXTRACT_KEY", matches = ".*")
    void getMasterKey_envVarAbsent_returnsEmpty() {
        Optional<String> result = CredentialEncryptor.getMasterKey();
        assertTrue(result.isEmpty(), "Expected Optional.empty() when CLM_EXTRACT_KEY is not set");
    }
}

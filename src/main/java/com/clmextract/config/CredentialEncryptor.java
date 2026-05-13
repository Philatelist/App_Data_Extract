package com.clmextract.config;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

public final class CredentialEncryptor {

    private static final String CIPHER_ALGO = "AES/GCM/NoPadding";
    private static final String KEY_ALGO = "AES";
    private static final String HASH_ALGO = "SHA-256";
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String PREFIX = "ENC(";
    private static final String SUFFIX = ")";

    private CredentialEncryptor() {
    }

    /**
     * Returns true if value is non-null, starts with "ENC(" and ends with ")".
     */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX) && value.endsWith(SUFFIX);
    }

    /**
     * Reads the CLM_EXTRACT_KEY environment variable.
     * Returns Optional.empty() if null or blank.
     */
    public static Optional<String> getMasterKey() {
        String key = System.getenv("CLM_EXTRACT_KEY");
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(key);
    }

    /**
     * Encrypts plaintext using AES-256-GCM with a key derived from masterKey.
     * Returns the result in "ENC(base64...)" format where the blob is IV || ciphertext.
     */
    public static String encrypt(String plaintext, String masterKey) {
        try {
            byte[] keyBytes = deriveKey(masterKey);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, KEY_ALGO);

            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] blob = concat(iv, ciphertext);
            return PREFIX + Base64.getEncoder().encodeToString(blob) + SUFFIX;
        } catch (Exception e) {
            throw new ConfigValidationException("Failed to encrypt credential: " + e.getMessage());
        }
    }

    /**
     * Decrypts an "ENC(base64...)" value using AES-256-GCM with a key derived from masterKey.
     * Throws ConfigValidationException on any failure.
     */
    public static String decrypt(String encryptedValue, String masterKey) {
        try {
            String inner = encryptedValue.substring(PREFIX.length(), encryptedValue.length() - SUFFIX.length());
            byte[] blob = Base64.getDecoder().decode(inner);

            byte[] iv = Arrays.copyOfRange(blob, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(blob, IV_LENGTH, blob.length);

            byte[] keyBytes = deriveKey(masterKey);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, KEY_ALGO);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ConfigValidationException("Failed to decrypt credential — check that CLM_EXTRACT_KEY is correct");
        }
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private static byte[] deriveKey(String masterKey) throws Exception {
        return MessageDigest.getInstance(HASH_ALGO).digest(masterKey.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}

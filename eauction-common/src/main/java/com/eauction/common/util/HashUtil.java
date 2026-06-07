package com.eauction.common.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class HashUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int    GCM_IV_LENGTH_BYTES    = 12;
    private static final int    GCM_TAG_LENGTH_BITS    = 128;

    private HashUtil() {}

    /** FIPS 180-4 SHA-512/256 — same 256-bit/64-hex-char output size as SHA-256 (fits existing *_hash VARCHAR(64) columns) but with a larger internal state and length-extension resistance. */
    public static String sha512_256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512/256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512/256 not available", e);
        }
    }

    public static SecretKey aesKey(byte[] keyBytes) {
        return new SecretKeySpec(keyBytes, "AES");
    }

    /** Encrypts with AES-256-GCM; returns base64(iv || ciphertext+tag) — self-contained, no separate IV bookkeeping needed by callers. */
    public static String encryptGcm(String plaintext, SecretKey key) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    /** Reverses {@link #encryptGcm} — throws (rather than returning garbage) if the GCM tag doesn't verify, i.e. wrong key or tampered ciphertext. */
    public static String decryptGcm(String encoded, SecretKey key) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(encoded));
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        }
    }

    public static String generateSalt() {
        byte[] salt = new byte[32];
        SECURE_RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public static String generateSecureToken() {
        byte[] token = new byte[64];
        SECURE_RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    public static String maskPan(String pan) {
        if (pan == null || pan.length() < 4) return "****";
        return "*".repeat(pan.length() - 4) + pan.substring(pan.length() - 4);
    }

    public static String maskAadhaar(String aadhaar) {
        if (aadhaar == null || aadhaar.length() < 4) return "****";
        return "XXXX-XXXX-" + aadhaar.substring(aadhaar.length() - 4);
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "****";
        int atIndex = email.indexOf('@');
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (local.length() <= 2) return local.charAt(0) + "*" + domain;
        return local.charAt(0) + "*".repeat(local.length() - 2) + local.charAt(local.length() - 1) + domain;
    }

    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 4) return "****";
        return "*".repeat(mobile.length() - 4) + mobile.substring(mobile.length() - 4);
    }

    public static String maskAccountNumber(String account) {
        if (account == null || account.length() < 4) return "****";
        return "XXXX" + account.substring(account.length() - 4);
    }
}

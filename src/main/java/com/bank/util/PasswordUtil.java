package com.bank.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Salted PBKDF2-HMAC-SHA256 password hashing (JDK built-in, no extra dependency).
 * Stored format: iterations:saltBase64:hashBase64
 */
public final class PasswordUtil {
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RNG = new SecureRandom();

    private PasswordUtil() {}

    public static String hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        RNG.nextBytes(salt);
        byte[] h = pbkdf2(password, salt, ITERATIONS);
        return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt)
                + ":" + Base64.getEncoder().encodeToString(h);
    }

    public static boolean verify(char[] password, String stored) {
        try {
            String[] p = stored.split(":");
            if (p.length != 3) return false;
            int iter = Integer.parseInt(p[0]);
            byte[] salt = Base64.getDecoder().decode(p[1]);
            byte[] expected = Base64.getDecoder().decode(p[2]);
            byte[] actual = pbkdf2(password, salt, iter);
            return MessageDigest.isEqual(expected, actual); // constant-time compare
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing unavailable", e);
        }
    }
}

package com.example.notification.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * HMAC-SHA256 utilities for webhook payload signing.
 *
 * Signature format: "sha256=<hex-encoded-hmac>"
 * Timestamp header: "X-HookFlow-Timestamp: <unix-epoch-seconds>"
 * Signature header: "X-HookFlow-Signature: sha256=<hex>"
 *
 * Verification procedure (for receivers):
 *   1. Read X-HookFlow-Timestamp.
 *   2. Reject if |now - timestamp| > 5 minutes (replay protection).
 *   3. Compute HMAC-SHA256 over "<timestamp>.<request-body-bytes>" using the integration secret.
 *   4. Compare hex output with X-HookFlow-Signature (constant-time).
 */
@UtilityClass
@Slf4j
public class HmacUtils {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int SECRET_BYTES = 32;

    /**
     * Generates a cryptographically secure random signing secret.
     * @return base64url-encoded 32-byte random secret
     */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Computes an HMAC-SHA256 over the signed payload.
     * Signed payload = "<timestampEpochSeconds>.<bodyBytes>"
     *
     * @param secret  base64url-encoded signing secret
     * @param timestampEpochSeconds Unix epoch seconds (to include in signature)
     * @param bodyBytes raw request body bytes
     * @return "sha256=<lowercase-hex>" ready for the signature header
     */
    public String computeSignature(String secret, long timestampEpochSeconds, byte[] bodyBytes) {
        try {
            byte[] secretBytes = Base64.getUrlDecoder().decode(secret);
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, ALGORITHM));

            // Signed payload: "<timestamp>.<body>"
            String prefix = timestampEpochSeconds + ".";
            mac.update(prefix.getBytes(StandardCharsets.UTF_8));
            mac.update(bodyBytes);

            byte[] hmac = mac.doFinal();
            return "sha256=" + bytesToHex(hmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     */
    public boolean verifySignature(String expected, String actual) {
        if (expected == null || actual == null) return false;
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

package com.smartfeeder.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import ru.tinkoff.kora.common.Component;

@Component
public final class HmacService {
    private static final String HMAC_SHA256 = "HmacSHA256";

    public String signHex(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] raw = mac.doFinal(body);
            return toHex(raw);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot calculate hmac", e);
        }
    }

    public boolean verifyHex(byte[] body, String secret, String providedHex) {
        if (providedHex == null || providedHex.isBlank()) {
            return false;
        }
        String expected = signHex(body, secret);
        return constantTimeEquals(expected, providedHex.trim().toLowerCase());
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}

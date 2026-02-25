package com.smartfeeder.security;

import com.smartfeeder.config.AppConfig;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import ru.tinkoff.kora.common.Component;

@Component
public final class SessionCookieService {
    private final String cookieName;
    private final boolean secure;
    private final HmacService hmacService;
    private final String signingSecret;

    public SessionCookieService(AppConfig appConfig, HmacService hmacService) {
        this.cookieName = appConfig.session().cookieName();
        this.secure = appConfig.session().cookieSecure();
        this.hmacService = hmacService;
        this.signingSecret = appConfig.session().secret();
    }

    public String cookieName() {
        return cookieName;
    }

    public String buildSessionCookie(String sessionId) {
        String signature = hmacService.signHex(sessionId.getBytes(StandardCharsets.UTF_8), signingSecret);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString((sessionId + "." + signature).getBytes(StandardCharsets.UTF_8));

        StringBuilder cookie = new StringBuilder();
        cookie.append(cookieName).append("=").append(value)
            .append("; Path=/")
            .append("; HttpOnly")
            .append("; SameSite=Lax");

        if (secure) {
            cookie.append("; Secure");
        }
        return cookie.toString();
    }

    public String buildLogoutCookie() {
        StringBuilder cookie = new StringBuilder();
        cookie.append(cookieName).append("=")
            .append("; Path=/")
            .append("; Max-Age=0")
            .append("; HttpOnly")
            .append("; SameSite=Lax");
        if (secure) {
            cookie.append("; Secure");
        }
        return cookie.toString();
    }

    public Optional<String> extractSessionId(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return Optional.empty();
        }

        String[] parts = cookieHeader.split(";");
        for (String part : parts) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2 || !cookieName.equals(kv[0].trim())) {
                continue;
            }

            try {
                byte[] decoded = Base64.getUrlDecoder().decode(kv[1].trim());
                String token = new String(decoded, StandardCharsets.UTF_8);
                String[] tokenParts = token.split("\\.", 2);
                if (tokenParts.length != 2) {
                    return Optional.empty();
                }

                String sessionId = tokenParts[0];
                String signature = tokenParts[1];
                boolean valid = hmacService.verifyHex(sessionId.getBytes(StandardCharsets.UTF_8), signingSecret, signature);
                return valid ? Optional.of(sessionId) : Optional.empty();
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }
}

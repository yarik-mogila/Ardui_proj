package com.smartfeeder.service;

import com.smartfeeder.config.AppConfig;
import com.smartfeeder.dao.StorageService;
import com.smartfeeder.security.PasswordService;
import com.smartfeeder.security.SessionCookieService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Pattern;
import ru.tinkoff.kora.common.Component;

@Component
public final class AuthService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final StorageService storage;
    private final PasswordService passwordService;
    private final SessionCookieService sessionCookieService;
    private final AppConfig appConfig;

    public AuthService(StorageService storage,
                       PasswordService passwordService,
                       SessionCookieService sessionCookieService,
                       AppConfig appConfig) {
        this.storage = storage;
        this.passwordService = passwordService;
        this.sessionCookieService = sessionCookieService;
        this.appConfig = appConfig;
    }

    public record AuthResult(String userId, String email, String sessionId, String setCookie) {
    }

    public AuthResult register(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        validatePassword(password);

        if (storage.findUserByEmail(normalizedEmail).isPresent()) {
            throw ApiException.conflict("email_already_exists");
        }

        String userId = storage.createUser(normalizedEmail, passwordService.hash(password));
        return createSessionResult(userId, normalizedEmail);
    }

    public AuthResult login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        var user = storage.findUserByEmail(normalizedEmail)
            .orElseThrow(() -> ApiException.unauthorized("invalid_credentials"));

        if (!passwordService.verify(password, user.passwordHash())) {
            throw ApiException.unauthorized("invalid_credentials");
        }

        return createSessionResult(user.id(), user.email());
    }

    public void logout(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            storage.deleteSession(sessionId);
        }
    }

    public Optional<StorageService.UserRow> findUserBySessionCookie(String cookieHeader) {
        var sessionId = sessionCookieService.extractSessionId(cookieHeader);
        if (sessionId.isEmpty()) {
            return Optional.empty();
        }

        var session = storage.findValidSession(sessionId.get());
        if (session.isEmpty()) {
            return Optional.empty();
        }

        return storage.findUserById(session.get().userId());
    }

    public StorageService.UserRow requireUser(String cookieHeader) {
        return findUserBySessionCookie(cookieHeader)
            .orElseThrow(() -> ApiException.unauthorized("auth_required"));
    }

    private AuthResult createSessionResult(String userId, String email) {
        Instant expiresAt = Instant.now().plus(appConfig.session().ttlHours(), ChronoUnit.HOURS);
        String sessionId = storage.createSession(userId, expiresAt);
        String cookie = sessionCookieService.buildSessionCookie(sessionId);
        return new AuthResult(userId, email, sessionId, cookie);
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            throw ApiException.badRequest("email_required");
        }
        String normalized = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw ApiException.badRequest("invalid_email");
        }
        return normalized;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw ApiException.badRequest("password_min_8");
        }
    }
}

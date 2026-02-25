package com.smartfeeder.config;

import ru.tinkoff.kora.config.common.annotation.ConfigSource;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigSource("app")
@ConfigValueExtractor
public interface AppConfig {
    String baseUrl();
    DeviceAuthConfig deviceAuth();
    SessionConfig session();
    SecurityConfig security();

    @ConfigValueExtractor
    interface DeviceAuthConfig {
        boolean signatureEnabled();
        int pollIntervalSec();
        int nonceWindowSec();
        int maxPollPerMinute();
    }

    @ConfigValueExtractor
    interface SessionConfig {
        String cookieName();
        int ttlHours();
        boolean cookieSecure();
        String secret();
    }

    @ConfigValueExtractor
    interface SecurityConfig {
        String encryptionKey();
    }
}

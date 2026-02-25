package com.smartfeeder;

import com.smartfeeder.config.AppConfig;

public final class TestAppConfig implements AppConfig {
    private final DeviceAuthConfig deviceAuth;
    private final SessionConfig session;
    private final SecurityConfig security;

    public TestAppConfig(boolean signatureEnabled, int nonceWindowSec, String sessionSecret, String encryptionKey) {
        this.deviceAuth = new DeviceAuthConfig() {
            @Override
            public boolean signatureEnabled() {
                return signatureEnabled;
            }

            @Override
            public int pollIntervalSec() {
                return 60;
            }

            @Override
            public int nonceWindowSec() {
                return nonceWindowSec;
            }

            @Override
            public int maxPollPerMinute() {
                return 120;
            }
        };
        this.session = new SessionConfig() {
            @Override
            public String cookieName() {
                return "sf_session";
            }

            @Override
            public int ttlHours() {
                return 24;
            }

            @Override
            public boolean cookieSecure() {
                return false;
            }

            @Override
            public String secret() {
                return sessionSecret;
            }
        };
        this.security = () -> encryptionKey;
    }

    @Override
    public String baseUrl() {
        return "http://localhost";
    }

    @Override
    public DeviceAuthConfig deviceAuth() {
        return deviceAuth;
    }

    @Override
    public SessionConfig session() {
        return session;
    }

    @Override
    public SecurityConfig security() {
        return security;
    }
}

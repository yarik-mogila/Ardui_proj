package com.smartfeeder.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfeeder.TestAppConfig;
import org.junit.jupiter.api.Test;

class SessionCookieServiceTest {

    @Test
    void createsAndParsesSignedCookie() {
        SessionCookieService cookieService = new SessionCookieService(
            new TestAppConfig(false, 300, "my-session-secret", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="),
            new HmacService()
        );

        String cookie = cookieService.buildSessionCookie("session-123");
        String cookieHeader = cookie + "; Path=/";

        assertThat(cookieService.extractSessionId(cookieHeader)).contains("session-123");
    }
}

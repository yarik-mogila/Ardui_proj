package com.smartfeeder.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartfeeder.TestAppConfig;
import com.smartfeeder.security.HmacService;
import com.smartfeeder.security.SecretHashService;
import org.junit.jupiter.api.Test;

class DeviceAuthServiceTest {

    @Test
    void signatureOffBypassesHeaderAndNonceChecks() {
        DeviceAuthService service = new DeviceAuthService(
            new TestAppConfig(false, 300, "session", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="),
            new HmacService(),
            new SecretHashService()
        );

        assertThatCode(() -> service.validate(
            "feeder-001",
            null,
            null,
            null,
            1700000000,
            "{}".getBytes(),
            "ignored",
            "ignored",
            new NoopNonceStore(true)
        )).doesNotThrowAnyException();
    }

    @Test
    void signatureOnRequiresValidHeadersNonceAndHmac() {
        HmacService hmac = new HmacService();
        SecretHashService hashService = new SecretHashService();

        DeviceAuthService service = new DeviceAuthService(
            new TestAppConfig(true, 300, "session", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="),
            hmac,
            hashService
        );

        byte[] body = "{\"a\":1}".getBytes();
        String secret = "device-secret";
        String sign = hmac.signHex(body, secret);
        String secretHash = hashService.sha256Hex(secret);

        assertThatCode(() -> service.validate(
            "feeder-001",
            "feeder-001",
            "nonce-1",
            sign,
            java.time.Instant.now().getEpochSecond(),
            body,
            secretHash,
            secret,
            new NoopNonceStore(true)
        )).doesNotThrowAnyException();
    }

    @Test
    void signatureOnRejectsReplayNonce() {
        HmacService hmac = new HmacService();
        SecretHashService hashService = new SecretHashService();

        DeviceAuthService service = new DeviceAuthService(
            new TestAppConfig(true, 300, "session", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="),
            hmac,
            hashService
        );

        byte[] body = "{\"a\":1}".getBytes();
        String secret = "device-secret";

        assertThatThrownBy(() -> service.validate(
            "feeder-001",
            "feeder-001",
            "nonce-dup",
            hmac.signHex(body, secret),
            java.time.Instant.now().getEpochSecond(),
            body,
            hashService.sha256Hex(secret),
            secret,
            new NoopNonceStore(false)
        ))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("replay_detected");
    }

    private record NoopNonceStore(boolean registerResult) implements DeviceAuthService.NonceStore {
        @Override
        public void purgeOldNonce(long minEpochExclusive) {
        }

        @Override
        public boolean registerNonce(String deviceId, String nonce, long tsEpoch) {
            return registerResult;
        }
    }
}

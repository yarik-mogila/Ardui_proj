package com.smartfeeder.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HmacServiceTest {

    private final HmacService hmacService = new HmacService();

    @Test
    void verifiesValidHmacAndRejectsInvalid() {
        byte[] body = "{\"deviceId\":\"feeder-001\"}".getBytes();
        String secret = "test-secret";

        String sign = hmacService.signHex(body, secret);

        assertThat(hmacService.verifyHex(body, secret, sign)).isTrue();
        assertThat(hmacService.verifyHex(body, secret, sign + "00")).isFalse();
        assertThat(hmacService.verifyHex(body, "wrong-secret", sign)).isFalse();
    }
}

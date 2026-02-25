package com.smartfeeder.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfeeder.TestAppConfig;
import org.junit.jupiter.api.Test;

class SecretCryptoServiceTest {

    @Test
    void encryptDecryptRoundTrip() {
        String b64Key = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
        SecretCryptoService cryptoService = new SecretCryptoService(
            new TestAppConfig(false, 300, "session-secret", b64Key)
        );

        String original = "very-secret-value";
        byte[] encrypted = cryptoService.encrypt(original);
        String decrypted = cryptoService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(original);
        assertThat(encrypted).isNotEmpty();
    }
}

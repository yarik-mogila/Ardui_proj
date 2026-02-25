package com.smartfeeder.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import ru.tinkoff.kora.common.Component;

@Component
public final class RandomSecretService {
    private final SecureRandom random = new SecureRandom();

    public String generateSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String generateId() {
        return UUID.randomUUID().toString();
    }
}

package com.smartfeeder.security;

import org.mindrot.jbcrypt.BCrypt;
import ru.tinkoff.kora.common.Component;

@Component
public final class PasswordService {
    public String hash(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(12));
    }

    public boolean verify(String rawPassword, String hash) {
        return BCrypt.checkpw(rawPassword, hash);
    }
}

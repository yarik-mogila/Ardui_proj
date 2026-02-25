package com.smartfeeder.config;

import ru.tinkoff.kora.config.common.annotation.ConfigSource;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigSource("db")
@ConfigValueExtractor
public interface DbConfig {
    String jdbcUrl();
    String username();
    String password();
    int maxPoolSize();
    String poolName();
}

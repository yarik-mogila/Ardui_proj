package com.smartfeeder.service;

import com.smartfeeder.config.AppConfig;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import ru.tinkoff.kora.common.Component;

@Component
public final class PollRateLimiter {
    private final int maxPerMinute;
    private final ConcurrentHashMap<String, CounterWindow> state = new ConcurrentHashMap<>();

    public PollRateLimiter(AppConfig appConfig) {
        this.maxPerMinute = appConfig.deviceAuth().maxPollPerMinute();
    }

    public boolean allow(String key) {
        long currentMinute = Instant.now().getEpochSecond() / 60;
        CounterWindow updated = state.compute(key, (k, current) -> {
            if (current == null || current.minute != currentMinute) {
                return new CounterWindow(currentMinute, 1);
            }
            return new CounterWindow(current.minute, current.count + 1);
        });

        return updated.count <= maxPerMinute;
    }

    private record CounterWindow(long minute, int count) {
    }
}

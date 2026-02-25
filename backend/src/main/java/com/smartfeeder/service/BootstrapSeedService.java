package com.smartfeeder.service;

import com.smartfeeder.dao.StorageService;
import com.smartfeeder.security.PasswordService;
import com.smartfeeder.security.SecretCryptoService;
import com.smartfeeder.security.SecretHashService;
import com.smartfeeder.util.Jsons;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.annotation.Root;

@Component
@Root
public final class BootstrapSeedService {
    private static final Logger logger = LoggerFactory.getLogger(BootstrapSeedService.class);

    private final StorageService storage;
    private final PasswordService passwordService;
    private final RandomSecretService randomSecretService;
    private final SecretHashService secretHashService;
    private final SecretCryptoService secretCryptoService;

    public BootstrapSeedService(StorageService storage,
                                MigrationRunner migrationRunner,
                                PasswordService passwordService,
                                RandomSecretService randomSecretService,
                                SecretHashService secretHashService,
                                SecretCryptoService secretCryptoService) {
        this.storage = storage;
        this.passwordService = passwordService;
        this.randomSecretService = randomSecretService;
        this.secretHashService = secretHashService;
        this.secretCryptoService = secretCryptoService;

        seed();
    }

    private void seed() {
        String demoEmail = "demo@smartfeeder.local";
        String demoPass = "demo12345";

        String demoUserId = storage.findUserIdByEmail(demoEmail)
            .orElseGet(() -> {
                String id = storage.createUser(demoEmail, passwordService.hash(demoPass));
                logger.info("Seed user created: {} / {}", demoEmail, demoPass);
                return id;
            });

        if (!storage.hasDevice("feeder-001")) {
            String secret = randomSecretService.generateSecret();
            storage.createDevice(
                demoUserId,
                "feeder-001",
                "Demo Feeder",
                secretHashService.sha256Hex(secret),
                secretCryptoService.encrypt(secret)
            );

            logger.info("Seed device created: feeder-001");
            logger.info("Seed device secret (shown once): {}", secret);
            storage.insertFeedLog("feeder-001", Instant.now(), "BOOT", "Seed device created", Jsons.stringify(Map.of("deviceId", "feeder-001")));
        }

        ensureProfileAndSchedule("feeder-001", "kitten", 900,
            List.of(new StorageService.ScheduleEventInput(8, 0, 900), new StorageService.ScheduleEventInput(13, 0, 900), new StorageService.ScheduleEventInput(19, 0, 900)));
        ensureProfileAndSchedule("feeder-001", "adult", 1200,
            List.of(new StorageService.ScheduleEventInput(8, 0, 1200), new StorageService.ScheduleEventInput(20, 0, 1200)));
        ensureProfileAndSchedule("feeder-001", "diet", 700,
            List.of(new StorageService.ScheduleEventInput(9, 0, 700), new StorageService.ScheduleEventInput(18, 0, 700)));

        storage.findProfileByName("feeder-001", "adult")
            .ifPresent(profile -> storage.setActiveProfile("feeder-001", profile.id()));
    }

    private void ensureProfileAndSchedule(String deviceId,
                                          String profileName,
                                          int defaultPortion,
                                          List<StorageService.ScheduleEventInput> defaults) {
        var profile = storage.findProfileByName(deviceId, profileName)
            .orElseGet(() -> storage.createProfile(deviceId, profileName, defaultPortion));

        if (storage.listScheduleByProfile(profile.id()).isEmpty()) {
            storage.replaceSchedule(profile.id(), defaults);
        }
    }
}

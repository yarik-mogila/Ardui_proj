package com.smartfeeder.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.smartfeeder.config.AppConfig;
import com.smartfeeder.dao.StorageService;
import com.smartfeeder.domain.PollApi;
import com.smartfeeder.security.SecretCryptoService;
import com.smartfeeder.util.Jsons;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.common.Component;

@Component
public final class DevicePollService {
    private static final Logger logger = LoggerFactory.getLogger(DevicePollService.class);

    private final StorageService storage;
    private final AppConfig appConfig;
    private final PollRateLimiter pollRateLimiter;
    private final DeviceAuthService deviceAuthService;
    private final SecretCryptoService secretCryptoService;

    public DevicePollService(StorageService storage,
                             AppConfig appConfig,
                             PollRateLimiter pollRateLimiter,
                             DeviceAuthService deviceAuthService,
                             SecretCryptoService secretCryptoService) {
        this.storage = storage;
        this.appConfig = appConfig;
        this.pollRateLimiter = pollRateLimiter;
        this.deviceAuthService = deviceAuthService;
        this.secretCryptoService = secretCryptoService;
    }

    public PollApi.PollResponse handlePoll(PollApi.PollRequest request,
                                           String headerDeviceId,
                                           String nonce,
                                           String signature) {
        if (request == null || request.deviceId() == null || request.deviceId().isBlank()) {
            throw ApiException.badRequest("device_id_required");
        }

        String deviceId = request.deviceId().trim();

        if (!pollRateLimiter.allow(deviceId)) {
            throw ApiException.tooManyRequests("poll_rate_limit_exceeded");
        }

        var device = storage.findDeviceById(deviceId)
            .orElseThrow(() -> ApiException.unauthorized("unknown_device"));

        String deviceSecret = secretCryptoService.decrypt(device.encryptedSecret());
        deviceAuthService.validate(
            deviceId,
            headerDeviceId,
            nonce,
            signature,
            request.ts(),
            Jsons.bytes(request),
            device.secretHash(),
            deviceSecret,
            new DeviceAuthService.NonceStore() {
                @Override
                public void purgeOldNonce(long minEpochExclusive) {
                    storage.purgeOldNonce(minEpochExclusive);
                }

                @Override
                public boolean registerNonce(String d, String n, long ts) {
                    return storage.registerNonce(d, n, ts);
                }
            }
        );

        String statusJson = Jsons.stringify(request.status() == null ? Map.of() : request.status());
        String firmware = request.status() == null ? null : request.status().fw();

        storage.updateDeviceStatus(deviceId, Instant.now(), statusJson, firmware);

        List<StorageService.FeedLogInput> logs = new ArrayList<>();
        if (request.log() != null) {
            for (var item : request.log()) {
                Instant ts = item.ts() > 0 ? Instant.ofEpochSecond(item.ts()) : Instant.now();
                logs.add(new StorageService.FeedLogInput(
                    ts,
                    sanitizeLogType(item.type()),
                    item.msg() == null ? "" : item.msg(),
                    Jsons.stringify(item.meta() == null ? Map.of() : item.meta())
                ));
            }
        }
        storage.insertFeedLogs(deviceId, logs);

        if (request.ack() != null && !request.ack().isEmpty()) {
            storage.ackCommands(deviceId, request.ack());
        }

        List<PollApi.PollCommand> commands = new ArrayList<>();
        for (var row : storage.fetchPendingAndMarkSent(deviceId, 10)) {
            commands.add(new PollApi.PollCommand(
                row.id(),
                row.commandType(),
                parsePayload(row.payloadJson())
            ));
        }

        String activeProfile = storage.getActiveProfileName(deviceId).orElse(null);

        List<PollApi.ProfileConfig> profiles = new ArrayList<>();
        for (var p : storage.listProfilesByDevice(deviceId)) {
            profiles.add(new PollApi.ProfileConfig(p.name(), p.defaultPortionMs()));
        }

        List<PollApi.ScheduleConfig> schedule = new ArrayList<>();
        for (var s : storage.listScheduleByDevice(deviceId)) {
            schedule.add(new PollApi.ScheduleConfig(
                s.profileName(),
                s.hh(),
                s.mm(),
                s.portionMs()
            ));
        }

        return new PollApi.PollResponse(
            Instant.now().getEpochSecond(),
            appConfig.deviceAuth().pollIntervalSec(),
            commands,
            new PollApi.PollConfig(activeProfile, profiles, schedule)
        );
    }

    private Map<String, Object> parsePayload(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            return Jsons.mapper().readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            logger.warn("Cannot parse command payload json", e);
            return Map.of();
        }
    }

    private static String sanitizeLogType(String type) {
        if (type == null || type.isBlank()) {
            return "INFO";
        }
        return type.trim().toUpperCase();
    }
}

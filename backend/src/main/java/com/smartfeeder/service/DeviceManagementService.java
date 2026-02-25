package com.smartfeeder.service;

import com.smartfeeder.dao.StorageService;
import com.smartfeeder.domain.AdminApi;
import com.smartfeeder.security.SecretCryptoService;
import com.smartfeeder.security.SecretHashService;
import com.smartfeeder.util.Jsons;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.common.Component;

@Component
public final class DeviceManagementService {
    private static final Logger logger = LoggerFactory.getLogger(DeviceManagementService.class);

    private final StorageService storage;
    private final RandomSecretService randomSecretService;
    private final SecretHashService secretHashService;
    private final SecretCryptoService secretCryptoService;

    public DeviceManagementService(StorageService storage,
                                   RandomSecretService randomSecretService,
                                   SecretHashService secretHashService,
                                   SecretCryptoService secretCryptoService) {
        this.storage = storage;
        this.randomSecretService = randomSecretService;
        this.secretHashService = secretHashService;
        this.secretCryptoService = secretCryptoService;
    }

    public List<AdminApi.DeviceSummary> listDevices(String userId) {
        Instant now = Instant.now();
        List<AdminApi.DeviceSummary> rows = new ArrayList<>();
        for (var row : storage.listDevicesByUser(userId)) {
            boolean online = row.lastSeenAt() != null && row.lastSeenAt().isAfter(now.minus(Duration.ofMinutes(2)));
            rows.add(new AdminApi.DeviceSummary(
                row.id(),
                row.name(),
                online,
                row.lastSeenAt(),
                row.rssi(),
                row.firmwareVersion()
            ));
        }
        return rows;
    }

    public AdminApi.DeviceSecretResponse createDevice(String userId, String deviceId, String name) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        String normalizedName = normalizeName(name);

        if (storage.hasDevice(normalizedDeviceId)) {
            throw ApiException.conflict("device_id_exists");
        }

        String secret = randomSecretService.generateSecret();
        storage.createDevice(
            userId,
            normalizedDeviceId,
            normalizedName,
            secretHashService.sha256Hex(secret),
            secretCryptoService.encrypt(secret)
        );

        logger.info("Device created: {}", normalizedDeviceId);
        return new AdminApi.DeviceSecretResponse(
            normalizedDeviceId,
            secret,
            "Secret is shown once. Store it in firmware config."
        );
    }

    public AdminApi.DeviceSecretResponse rotateSecret(String userId, String deviceId) {
        storage.findDeviceByIdAndUser(deviceId, userId)
            .orElseThrow(() -> ApiException.notFound("device_not_found"));

        String secret = randomSecretService.generateSecret();
        storage.rotateDeviceSecret(deviceId, secretHashService.sha256Hex(secret), secretCryptoService.encrypt(secret));
        storage.insertFeedLog(deviceId, Instant.now(), "INFO", "Secret rotated", "{}");

        return new AdminApi.DeviceSecretResponse(
            deviceId,
            secret,
            "Secret rotated and shown once."
        );
    }

    public AdminApi.DeviceDetails getDeviceDetails(String userId, String deviceId) {
        var device = storage.findDeviceByIdAndUser(deviceId, userId)
            .orElseThrow(() -> ApiException.notFound("device_not_found"));

        List<AdminApi.ProfileRecord> profiles = new ArrayList<>();
        for (var profile : storage.listProfilesByDevice(deviceId)) {
            profiles.add(new AdminApi.ProfileRecord(
                profile.id(),
                profile.name(),
                profile.defaultPortionMs()
            ));
        }

        List<AdminApi.ScheduleRecord> schedule = new ArrayList<>();
        for (var item : storage.listScheduleByDevice(deviceId)) {
            schedule.add(new AdminApi.ScheduleRecord(
                item.id(),
                item.profileId(),
                item.profileName(),
                item.hh(),
                item.mm(),
                item.portionMs()
            ));
        }

        Instant now = Instant.now();
        boolean online = device.lastSeenAt() != null && device.lastSeenAt().isAfter(now.minus(Duration.ofMinutes(2)));

        Map<String, Object> status = Map.of();
        if (device.lastStatusJson() != null && !device.lastStatusJson().isBlank()) {
            status = Jsons.mapper().convertValue(parseJsonObject(device.lastStatusJson()), Map.class);
        }

        String activeProfileName = storage.getActiveProfileName(deviceId).orElse(null);

        return new AdminApi.DeviceDetails(
            device.id(),
            device.name(),
            online,
            device.lastSeenAt(),
            status,
            activeProfileName,
            profiles,
            schedule
        );
    }

    public AdminApi.ProfileRecord createProfile(String userId, String deviceId, String name, int defaultPortionMs) {
        storage.findDeviceByIdAndUser(deviceId, userId)
            .orElseThrow(() -> ApiException.notFound("device_not_found"));

        if (defaultPortionMs <= 0) {
            throw ApiException.badRequest("default_portion_ms_must_be_positive");
        }

        String normalizedName = normalizeProfileName(name);
        if (storage.findProfileByName(deviceId, normalizedName).isPresent()) {
            throw ApiException.conflict("profile_name_exists");
        }

        var row = storage.createProfile(deviceId, normalizedName, defaultPortionMs);
        return new AdminApi.ProfileRecord(row.id(), row.name(), row.defaultPortionMs());
    }

    public void updateProfile(String userId, String profileId, String name, Integer defaultPortionMs) {
        if (defaultPortionMs != null && defaultPortionMs <= 0) {
            throw ApiException.badRequest("default_portion_ms_must_be_positive");
        }

        var beforeUpdate = storage.findProfileByIdAndUser(profileId, userId)
            .orElseThrow(() -> ApiException.notFound("profile_not_found"));

        storage.updateProfile(profileId, userId, name == null ? null : normalizeProfileName(name), defaultPortionMs);

        var updated = storage.findProfileByIdAndUser(profileId, userId).orElse(beforeUpdate);
        if (defaultPortionMs != null) {
            storage.enqueueCommand(
                updated.deviceId(),
                "SET_DEFAULT_PORTION",
                Jsons.stringify(Map.of(
                    "profileName", updated.name(),
                    "defaultPortionMs", updated.defaultPortionMs()
                ))
            );
        }
    }

    public void deleteProfile(String userId, String profileId) {
        storage.findProfileByIdAndUser(profileId, userId)
            .orElseThrow(() -> ApiException.notFound("profile_not_found"));

        storage.deleteProfile(profileId, userId);
    }

    public void replaceSchedule(String userId, String profileId, List<AdminApi.ScheduleEventCreate> events) {
        var profile = storage.findProfileByIdAndUser(profileId, userId)
            .orElseThrow(() -> ApiException.notFound("profile_not_found"));

        List<StorageService.ScheduleEventInput> toSave = new ArrayList<>();
        for (var event : events) {
            validateScheduleEvent(event.hh(), event.mm(), event.portionMs());
            toSave.add(new StorageService.ScheduleEventInput(event.hh(), event.mm(), event.portionMs()));
        }

        storage.replaceSchedule(profileId, toSave);
        storage.enqueueCommand(
            profile.deviceId(),
            "SET_SCHEDULE",
            Jsons.stringify(Map.of(
                "profileName", profile.name(),
                "events", events
            ))
        );
        storage.insertFeedLog(
            profile.deviceId(),
            Instant.now(),
            "SCHEDULE_UPDATED",
            "Schedule updated for profile " + profile.name(),
            Jsons.stringify(Map.of("profileName", profile.name(), "eventsCount", events.size()))
        );
    }

    public void setActiveProfile(String userId, String deviceId, String profileName) {
        storage.findDeviceByIdAndUser(deviceId, userId)
            .orElseThrow(() -> ApiException.notFound("device_not_found"));

        var profile = storage.findProfileByName(deviceId, normalizeProfileName(profileName))
            .orElseThrow(() -> ApiException.notFound("profile_not_found"));

        storage.setActiveProfile(deviceId, profile.id());
        storage.enqueueCommand(deviceId, "SET_PROFILE", Jsons.stringify(Map.of("profileName", profile.name())));
        storage.insertFeedLog(deviceId, Instant.now(), "PROFILE_CHANGED", "Active profile changed to " + profile.name(), "{}");
    }

    public String feedNow(String userId, String deviceId, Integer requestedPortionMs) {
        storage.findDeviceByIdAndUser(deviceId, userId)
            .orElseThrow(() -> ApiException.notFound("device_not_found"));

        int portion = resolvePortion(deviceId, requestedPortionMs);
        String commandId = storage.enqueueCommand(deviceId, "FEED_NOW", Jsons.stringify(Map.of("portionMs", portion)));
        storage.insertFeedLog(deviceId, Instant.now(), "MANUAL_FEED", "Manual feed requested", Jsons.stringify(Map.of("portionMs", portion)));
        return commandId;
    }

    public List<AdminApi.FeedLogRecord> listLogs(String userId,
                                                 String deviceId,
                                                 String type,
                                                 String query,
                                                 int page,
                                                 int size) {
        storage.findDeviceByIdAndUser(deviceId, userId)
            .orElseThrow(() -> ApiException.notFound("device_not_found"));

        int boundedSize = Math.max(1, Math.min(size, 200));
        int offset = Math.max(page, 0) * boundedSize;

        List<AdminApi.FeedLogRecord> result = new ArrayList<>();
        for (var row : storage.listFeedLogs(deviceId, type, query, boundedSize, offset)) {
            result.add(new AdminApi.FeedLogRecord(
                row.id(),
                row.ts(),
                row.type(),
                row.message(),
                Jsons.mapper().convertValue(parseJsonObject(row.metaJson()), Map.class)
            ));
        }

        return result;
    }

    private int resolvePortion(String deviceId, Integer requestedPortionMs) {
        if (requestedPortionMs != null) {
            if (requestedPortionMs <= 0) {
                throw ApiException.badRequest("portion_ms_must_be_positive");
            }
            return requestedPortionMs;
        }

        String active = storage.getActiveProfileName(deviceId).orElse(null);
        if (active != null) {
            Optional<StorageService.ProfileRow> profile = storage.findProfileByName(deviceId, active);
            if (profile.isPresent()) {
                return profile.get().defaultPortionMs();
            }
        }

        return storage.listProfilesByDevice(deviceId).stream()
            .findFirst()
            .map(StorageService.ProfileRow::defaultPortionMs)
            .orElse(1000);
    }

    private static void validateScheduleEvent(int hh, int mm, int portionMs) {
        if (hh < 0 || hh > 23) {
            throw ApiException.badRequest("hh_must_be_0_23");
        }
        if (mm < 0 || mm > 59) {
            throw ApiException.badRequest("mm_must_be_0_59");
        }
        if (portionMs <= 0) {
            throw ApiException.badRequest("portion_ms_must_be_positive");
        }
    }

    private static String normalizeProfileName(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("profile_name_required");
        }
        return value.trim();
    }

    private static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("name_required");
        }
        return value.trim();
    }

    private static String normalizeDeviceId(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("device_id_required");
        }
        return value.trim();
    }

    private Object parseJsonObject(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            return Jsons.mapper().readTree(json);
        } catch (Exception e) {
            logger.warn("Cannot parse json payload", e);
            return Map.of();
        }
    }
}

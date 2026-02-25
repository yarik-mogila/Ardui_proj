package com.smartfeeder.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import ru.tinkoff.kora.json.common.annotation.Json;

public final class AdminApi {
    private AdminApi() {
    }

    @Json
    public record RegisterRequest(String email, String password) {
    }

    @Json
    public record LoginRequest(String email, String password) {
    }

    @Json
    public record AuthResponse(String userId, String email) {
    }

    @Json
    public record CreateDeviceRequest(String deviceId, String name) {
    }

    @Json
    public record DeviceSecretResponse(String deviceId, String secret, String warning) {
    }

    @Json
    public record DeviceSummary(String deviceId,
                                String name,
                                boolean online,
                                Instant lastSeenAt,
                                Integer rssi,
                                String firmwareVersion) {
    }

    @Json
    public record DeviceDetails(String deviceId,
                                String name,
                                boolean online,
                                Instant lastSeenAt,
                                Map<String, Object> status,
                                String activeProfile,
                                List<ProfileRecord> profiles,
                                List<ScheduleRecord> schedule) {
    }

    @Json
    public record ProfileCreateRequest(String name, int defaultPortionMs) {
    }

    @Json
    public record ProfileUpdateRequest(String name, Integer defaultPortionMs) {
    }

    @Json
    public record ProfileRecord(String id, String name, int defaultPortionMs) {
    }

    @Json
    public record ScheduleReplaceRequest(List<ScheduleEventCreate> events) {
    }

    @Json
    public record ScheduleEventCreate(int hh, int mm, int portionMs) {
    }

    @Json
    public record ScheduleRecord(String id,
                                 String profileId,
                                 String profileName,
                                 int hh,
                                 int mm,
                                 int portionMs) {
    }

    @Json
    public record ActiveProfileRequest(String profileName) {
    }

    @Json
    public record FeedNowRequest(Integer portionMs) {
    }

    @Json
    public record FeedLogRecord(String id,
                                Instant ts,
                                String type,
                                String message,
                                Map<String, Object> meta) {
    }

    @Json
    public record MessageResponse(String message) {
    }
}

package com.smartfeeder.domain;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import ru.tinkoff.kora.json.common.annotation.Json;

public final class PollApi {
    private PollApi() {
    }

    @Json
    public record PollRequest(String deviceId,
                              long ts,
                              PollStatus status,
                              List<PollLogItem> log,
                              List<String> ack) {
    }

    @Json
    public record PollStatus(String fw,
                             long uptimeSec,
                             int rssi,
                             @Nullable String error,
                             @Nullable Long lastFeedTs) {
    }

    @Json
    public record PollLogItem(long ts,
                              String type,
                              String msg,
                              @Nullable Map<String, Object> meta) {
    }

    @Json
    public record PollResponse(long serverTime,
                               int intervalSec,
                               List<PollCommand> commands,
                               PollConfig config) {
    }

    @Json
    public record PollCommand(String id,
                              String commandType,
                              Map<String, Object> payloadJson) {
    }

    @Json
    public record PollConfig(String activeProfile,
                             List<ProfileConfig> profiles,
                             List<ScheduleConfig> schedule) {
    }

    @Json
    public record ProfileConfig(String name,
                                int defaultPortionMs) {
    }

    @Json
    public record ScheduleConfig(String profileName,
                                 int hh,
                                 int mm,
                                 int portionMs) {
    }
}

package com.smartfeeder.controller;

import com.smartfeeder.domain.PollApi;
import com.smartfeeder.service.ApiException;
import com.smartfeeder.service.DevicePollService;
import com.smartfeeder.util.HttpResponseFactory;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.annotation.Header;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;
import ru.tinkoff.kora.json.common.annotation.Json;

@Component
@HttpController
public final class DeviceController {
    private final DevicePollService devicePollService;
    private final HttpResponseFactory responses;

    public DeviceController(DevicePollService devicePollService, HttpResponseFactory responses) {
        this.devicePollService = devicePollService;
        this.responses = responses;
    }

    @HttpRoute(method = HttpMethod.POST, path = "/api/device/poll")
    public HttpServerResponse poll(@Json PollApi.PollRequest request,
                                   @Nullable @Header("X-Device-Id") String headerDeviceId,
                                   @Nullable @Header("X-Nonce") String nonce,
                                   @Nullable @Header("X-Sign") String signature) {
        try {
            if (request == null) {
                throw ApiException.badRequest("request_body_required");
            }
            PollApi.PollResponse response = devicePollService.handlePoll(request, headerDeviceId, nonce, signature);
            return responses.json(200, response);
        } catch (ApiException e) {
            return responses.fromException(e);
        } catch (Exception e) {
            return responses.internalError(e);
        }
    }
}

package com.smartfeeder.controller;

import com.smartfeeder.config.AppConfig;
import com.smartfeeder.domain.AdminApi;
import com.smartfeeder.service.ApiException;
import com.smartfeeder.service.AuthService;
import com.smartfeeder.service.DeviceManagementService;
import com.smartfeeder.util.HttpResponseFactory;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.annotation.Header;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.common.annotation.Path;
import ru.tinkoff.kora.http.common.annotation.Query;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;
import ru.tinkoff.kora.json.common.annotation.Json;

@Component
@HttpController
public final class AdminController {
    private final AuthService authService;
    private final DeviceManagementService deviceManagementService;
    private final HttpResponseFactory responses;
    private final AppConfig appConfig;

    public AdminController(AuthService authService,
                           DeviceManagementService deviceManagementService,
                           HttpResponseFactory responses,
                           AppConfig appConfig) {
        this.authService = authService;
        this.deviceManagementService = deviceManagementService;
        this.responses = responses;
        this.appConfig = appConfig;
    }

    @HttpRoute(method = HttpMethod.GET, path = "/api/admin/devices")
    public HttpServerResponse listDevices(@Nullable @Header("Cookie") String cookieHeader) {
        try {
            var user = authService.requireUser(cookieHeader);
            List<AdminApi.DeviceSummary> devices = deviceManagementService.listDevices(user.id());
            return responses.json(200, devices);
        } catch (ApiException e) {
            return responses.fromException(e);
        } catch (Exception e) {
            return responses.internalError(e);
        }
    }

    @HttpRoute(method = HttpMethod.POST, path = "/api/admin/devices")
    public HttpServerResponse createDevice(@Nullable @Header("Cookie") String cookieHeader,
                                           @Json AdminApi.CreateDeviceRequest request) {
        try {
            if (request == null) {
                throw ApiException.badRequest("request_body_required");
            }
            var user = authService.requireUser(cookieHeader);
            var result = deviceManagementService.createDevice(user.id(), request.deviceId(), request.name());
            return responses.json(200, result);
        } catch (ApiException e) {
            return responses.fromException(e);
        } catch (Exception e) {
            return responses.internalError(e);
        }
    }

    @HttpRoute(method = HttpMethod.GET, path = "/api/admin/devices/{deviceId}")
    public HttpServerResponse getDevice(@Nullable @Header("Cookie") String cookieHeader,
                                        @Path("deviceId") String deviceId) {
        try {
            var user = authService.requireUser(cookieHeader);
            var details = deviceManagementService.getDeviceDetails(user.id(), deviceId);
            return responses.json(200, details);
        } catch (ApiException e) {
            return responses.fromException(e);
        } catch (Exception e) {
            return responses.internalError(e);
        }
    }

    @HttpRoute(method = HttpMethod.POST, path = "/api/admin/devices/{deviceId}/rotate-secret")
    public HttpServerResponse rotateSecret(@Nullable @Header("Cookie") String cookieHeader,
                                           @Path("deviceId") String deviceId) {
        try {
            var user = authService.requireUser(cookieHeader);
            var result = deviceManagementService.rotateSecret(user.id(), deviceId);
            return responses.json(200, result);
        } catch (ApiException e) {
            return responses.fromException(e);
        } catch (Exception e) {
            return responses.internalError(e);
        }
    }

    @HttpRoute(method = HttpMethod.POST, path = "/api/admin/devices/{deviceId}/profiles")
    public HttpServerResponse createProfile(@Nullable @Header("Cookie") String cookieHeader,
                                            @Path("deviceId") String deviceId,
                                            @Json AdminApi.ProfileCreateRequest request) {
        try {
            if (request == null) {
                throw ApiException.badRequest("request_body_required");
            }
            var user = authService.requireUser(cookieHeader);
            var result = deviceManagementService.createProfile(user.id(), deviceId, request.name(), request.defaultPortionMs());
            return responses.json(200, result);
        } catch (ApiException e) {
            return responses.fromException(e);
        } catch (Exception e) {
            return responses.internalError(e);
        }
    }

    @HttpRoute(method = HttpMethod.PATCH, path = "/api/admin/profiles/{profileId}")
    public HttpServerResponse updateProfile(@Nullable @Header("Cookie") String cookieHeader,
                                            @Path("profileId") String profileId,
                                            @Json AdminApi.ProfileUpdateRequest request) {
        try {
            if (request == null) {
                throw ApiException.badRequest("request_body_required");
            }
            var user = authService.requireUser(cookieHeader);
            deviceManagementService.updateProfile(user.id(), profileId, request.name(), request.defaultPortionMs());
            return responses.json(200, new AdminApi.MessageResponse("ok"));
        } catch (ApiException e) {
            return responses.fromException(e);
        } catch (Exception e) {
            return responses.internalError(e);
        }
    }

    @HttpRoute(method = HttpMethod.DELETE, path = "/api/admin/profiles/{profileId}")
    public HttpServerResponse deleteProfile(@Nullable @Header("Cookie") String cookieHeader,
                                            @Path("profileId") String profileId) {
        try {
            var user = authService.requireUser(cookieHeader);
            deviceManagementService.deleteProfile(user.id(), profileId);
            return responses.json(200, new AdminApi.MessageResponse("ok"));
        } catch (ApiException e) {
            return responses.fromException(e);
        } catch (Exception e) {
            return responses.internalError(e);
        }
    }

    @HttpRoute(method = HttpMethod.PUT, path = "/api/admin/profiles/{profileId}/schedule")
    public HttpServerResponse replaceSchedule(@Nullable @Header("Cookie") String cookieHeader,
                                              @Path("profileId") String profileId,
                                              @Json AdminApi.ScheduleReplaceRequest request) {
        try {
            if (request == null) {
                throw ApiException.badRequest("request_body_required");
            }
            var user = authService.requireUser(cookieHeader);
            deviceManagementService.replaceSchedule(user.id(), profileId, request.events() == null ? List.of() : request.events());
            return responses.json(200, new AdminApi.MessageResponse("ok"));
        } catch (ApiException e) {
            return responses.fromException(e);
        } catch (Exception e) {
            return responses.internalError(e);
        }
    }

    @HttpRoute(method = HttpMethod.POST, path = "/api/admin/devices/{deviceId}/active-profile")
    public HttpServerResponse setActiveProfile(@Nullable @Header("Cookie") String cookieHeader,
                                               @Path("deviceId") String deviceId,
                                               @Json AdminApi.ActiveProfileRequest request) {
        try {
            if (request == null) {
                throw ApiException.badRequest("request_body_required");
            }
            var user = authService.requireUser(cookieHeader);
            deviceManagementService.setActiveProfile(user.id(), deviceId, request.profileName());
            return responses.json(200, new AdminApi.MessageResponse("ok"));
        } catch (ApiException e) {
            return responses.fromException(e);
        } catch (Exception e) {
            return responses.internalError(e);
        }
    }

    @HttpRoute(method = HttpMethod.POST, path = "/api/admin/devices/{deviceId}/feed-now")
    public HttpServerResponse feedNow(@Nullable @Header("Cookie") String cookieHeader,
                                      @Path("deviceId") String deviceId,
                                      @Json AdminApi.FeedNowRequest request) {
        try {
            var user = authService.requireUser(cookieHeader);
            String commandId = deviceManagementService.feedNow(user.id(), deviceId, request == null ? null : request.portionMs());
            return responses.json(200, Map.of("commandId", commandId));
        } catch (ApiException e) {
            return responses.fromException(e);
        } catch (Exception e) {
            return responses.internalError(e);
        }
    }

    @HttpRoute(method = HttpMethod.GET, path = "/api/admin/devices/{deviceId}/logs")
    public HttpServerResponse listLogs(@Nullable @Header("Cookie") String cookieHeader,
                                       @Path("deviceId") String deviceId,
                                       @Nullable @Query("type") String type,
                                       @Nullable @Query("q") String q,
                                       @Nullable @Query("page") Integer page,
                                       @Nullable @Query("size") Integer size) {
        try {
            var user = authService.requireUser(cookieHeader);
            int resolvedPage = page == null ? 0 : page;
            int resolvedSize = size == null ? 50 : size;
            var logs = deviceManagementService.listLogs(user.id(), deviceId, type, q, resolvedPage, resolvedSize);
            return responses.json(200, logs);
        } catch (ApiException e) {
            return responses.fromException(e);
        } catch (Exception e) {
            return responses.internalError(e);
        }
    }

    @HttpRoute(method = HttpMethod.GET, path = "/api/admin/security/config")
    public HttpServerResponse securityConfig(@Nullable @Header("Cookie") String cookieHeader) {
        try {
            authService.requireUser(cookieHeader);
            boolean signatureEnabled = appConfig.deviceAuth().signatureEnabled();
            return responses.json(200, Map.of(
                "signatureEnabled", signatureEnabled,
                "nonceEnabled", signatureEnabled
            ));
        } catch (ApiException e) {
            return responses.fromException(e);
        } catch (Exception e) {
            return responses.internalError(e);
        }
    }
}

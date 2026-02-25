package com.smartfeeder.controller;

import com.smartfeeder.domain.AdminApi;
import com.smartfeeder.security.SessionCookieService;
import com.smartfeeder.service.ApiException;
import com.smartfeeder.service.AuthService;
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
public final class AuthController {
    private final AuthService authService;
    private final SessionCookieService sessionCookieService;
    private final HttpResponseFactory responses;

    public AuthController(AuthService authService,
                          SessionCookieService sessionCookieService,
                          HttpResponseFactory responses) {
        this.authService = authService;
        this.sessionCookieService = sessionCookieService;
        this.responses = responses;
    }

    @HttpRoute(method = HttpMethod.POST, path = "/api/auth/register")
    public HttpServerResponse register(@Json AdminApi.RegisterRequest request) {
        try {
            if (request == null) {
                throw ApiException.badRequest("request_body_required");
            }
            var result = authService.register(request.email(), request.password());
            return responses.jsonWithCookie(
                200,
                new AdminApi.AuthResponse(result.userId(), result.email()),
                result.setCookie()
            );
        } catch (ApiException e) {
            return responses.fromException(e);
        } catch (Exception e) {
            return responses.internalError(e);
        }
    }

    @HttpRoute(method = HttpMethod.POST, path = "/api/auth/login")
    public HttpServerResponse login(@Json AdminApi.LoginRequest request) {
        try {
            if (request == null) {
                throw ApiException.badRequest("request_body_required");
            }
            var result = authService.login(request.email(), request.password());
            return responses.jsonWithCookie(
                200,
                new AdminApi.AuthResponse(result.userId(), result.email()),
                result.setCookie()
            );
        } catch (ApiException e) {
            return responses.fromException(e);
        } catch (Exception e) {
            return responses.internalError(e);
        }
    }

    @HttpRoute(method = HttpMethod.POST, path = "/api/auth/logout")
    public HttpServerResponse logout(@Nullable @Header("Cookie") String cookieHeader) {
        try {
            var sessionId = sessionCookieService.extractSessionId(cookieHeader).orElse(null);
            authService.logout(sessionId);
            return responses.noContentWithCookie(sessionCookieService.buildLogoutCookie());
        } catch (ApiException e) {
            return responses.fromException(e);
        } catch (Exception e) {
            return responses.internalError(e);
        }
    }

    @HttpRoute(method = HttpMethod.GET, path = "/api/auth/me")
    public HttpServerResponse me(@Nullable @Header("Cookie") String cookieHeader) {
        try {
            var user = authService.requireUser(cookieHeader);
            return responses.json(200, new AdminApi.AuthResponse(user.id(), user.email()));
        } catch (ApiException e) {
            return responses.fromException(e);
        } catch (Exception e) {
            return responses.internalError(e);
        }
    }
}

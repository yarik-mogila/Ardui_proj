package com.smartfeeder.util;

import com.smartfeeder.service.ApiException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.SimpleHttpServerResponse;

@Component
public final class HttpResponseFactory {
    private static final Logger logger = LoggerFactory.getLogger(HttpResponseFactory.class);

    public HttpServerResponse json(int statusCode, Object value) {
        return new SimpleHttpServerResponse(
            statusCode,
            HttpHeaders.of("Content-Type", "application/json; charset=utf-8"),
            HttpBody.plaintext(Jsons.stringify(value))
        );
    }

    public HttpServerResponse jsonWithCookie(int statusCode, Object value, String cookieHeader) {
        return new SimpleHttpServerResponse(
            statusCode,
            HttpHeaders.of(
                "Content-Type", "application/json; charset=utf-8",
                "Set-Cookie", cookieHeader
            ),
            HttpBody.plaintext(Jsons.stringify(value))
        );
    }

    public HttpServerResponse noContentWithCookie(String cookieHeader) {
        return new SimpleHttpServerResponse(
            204,
            HttpHeaders.of("Set-Cookie", cookieHeader),
            HttpBody.empty()
        );
    }

    public HttpServerResponse fromException(ApiException e) {
        return json(e.status(), Map.of("error", e.publicMessage()));
    }

    public HttpServerResponse internalError(Exception e) {
        logger.error("Unhandled server error", e);
        return json(500, Map.of("error", "internal_error"));
    }
}

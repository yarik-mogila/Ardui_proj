package com.smartfeeder.service;

public final class ApiException extends RuntimeException {
    private final int status;
    private final String publicMessage;

    public ApiException(int status, String publicMessage) {
        super(publicMessage);
        this.status = status;
        this.publicMessage = publicMessage;
    }

    public int status() {
        return status;
    }

    public String publicMessage() {
        return publicMessage;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(401, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(403, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, message);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(429, message);
    }
}

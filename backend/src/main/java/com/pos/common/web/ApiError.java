package com.pos.common.web;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Uniform error body. {@code code} is a stable machine-readable reason (e.g. INSUFFICIENT_STOCK) for
 * errors clients may want to react to; {@code fieldErrors} (field to message) is present only for
 * validation and conflict errors that belong to specific request fields.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp, int status, String error, String message, String code, Map<String, String> fieldErrors) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, message, null, null);
    }

    public static ApiError of(int status, String error, String message, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, null, fieldErrors);
    }

    public static ApiError of(
            int status, String error, String message, String code, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, code, fieldErrors);
    }
}

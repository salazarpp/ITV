package com.pokesync.presentation.exception;

import java.time.Instant;
import org.slf4j.MDC;

public record ApiError(int status, String code, String message, Instant timestamp, String requestId) {
    public static ApiError of(int status, String code, String message) {
        return new ApiError(status, code, message, Instant.now(), MDC.get("requestId"));
    }
}

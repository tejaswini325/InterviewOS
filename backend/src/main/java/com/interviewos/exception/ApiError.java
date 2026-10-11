package com.interviewos.exception;

import java.time.Instant;
import java.util.Map;

public record ApiError(Instant timestamp, int status, String code, String message,
                       Map<String, String> fieldErrors, Map<String, Object> extra) {
    public static ApiError of(int status, String code, String message) {
        return new ApiError(Instant.now(), status, code, message, null, null);
    }
}

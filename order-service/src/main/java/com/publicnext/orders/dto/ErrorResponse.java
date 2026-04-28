package com.publicnext.orders.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String message,
        List<String> errors
) {
    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(Instant.now(), status, message, List.of());
    }

    public static ErrorResponse of(int status, String message, List<String> errors) {
        return new ErrorResponse(Instant.now(), status, message, errors);
    }
}

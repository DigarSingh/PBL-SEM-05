package com.shopsmart.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Uniform error envelope:
 * {@code {"success": false, "message": "...", "code": "...", "timestamp": "...", "path": "..."}}.
 * Field-level validation problems are listed in {@code errors}; structured context
 * (e.g. which items are out of stock) goes in {@code details}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        boolean success,
        String message,
        String code,
        Instant timestamp,
        String path,
        String requestId,
        List<FieldViolation> errors,
        Map<String, Object> details) {

    public record FieldViolation(String field, String message) {
    }
}

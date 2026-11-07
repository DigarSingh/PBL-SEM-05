package com.shopsmart.common.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Base class for expected, client-facing failures. The message is safe to show to users;
 * {@link #getDetails()} carries optional structured context for clients.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, ErrorCode code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiException(HttpStatus status, ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, message);
    }

    public static ApiException notFound(String resource) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, resource + " not found");
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message);
    }

    public static ApiException duplicate(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_RESOURCE, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, message);
    }

    public static ApiException businessRule(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}

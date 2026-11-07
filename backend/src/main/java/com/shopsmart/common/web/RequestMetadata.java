package com.shopsmart.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Client metadata of the current HTTP request. The remote address already reflects
 * {@code X-Forwarded-For} from trusted proxies (server.forward-headers-strategy=native).
 */
public final class RequestMetadata {

    private static final int MAX_USER_AGENT = 255;

    private RequestMetadata() {
    }

    public static Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.of(attributes.getRequest());
        }
        return Optional.empty();
    }

    public static String clientIp() {
        return currentRequest().map(HttpServletRequest::getRemoteAddr).orElse(null);
    }

    public static String userAgent() {
        return currentRequest()
                .map(request -> request.getHeader("User-Agent"))
                .map(value -> value.length() > MAX_USER_AGENT ? value.substring(0, MAX_USER_AGENT) : value)
                .orElse(null);
    }
}

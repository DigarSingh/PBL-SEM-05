package com.shopsmart.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shopsmart.common.exception.ErrorCode;
import com.shopsmart.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-client-IP token-bucket rate limiting. Credential endpoints get a much stricter
 * budget than the general API. Buckets live in a bounded cache that forgets idle clients.
 * <p>
 * The limiter is in-memory, i.e. per instance; a multi-instance deployment should move
 * buckets to a shared store (Bucket4j supports JCache/Redis backends).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> CREDENTIAL_ENDPOINTS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/verify-email",
            "/api/auth/resend-verification");
    private static final String WEBHOOK_PATH = "/api/payments/razorpay/webhook";

    private final AppProperties.RateLimit limits;
    private final SecurityErrorWriter writer;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    public RateLimitFilter(AppProperties properties, SecurityErrorWriter writer) {
        this.limits = properties.rateLimit();
        this.writer = writer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !limits.enabled()
                || !path.startsWith("/api/")
                || WEBHOOK_PATH.equals(path)
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean credentialEndpoint = "POST".equalsIgnoreCase(request.getMethod())
                && CREDENTIAL_ENDPOINTS.contains(request.getRequestURI());
        int perMinute = credentialEndpoint ? limits.authRequestsPerMinute() : limits.apiRequestsPerMinute();
        String key = (credentialEndpoint ? "auth:" : "api:") + request.getRemoteAddr();
        Bucket bucket = buckets.get(key, ignored -> newBucket(perMinute));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        writer.write(request, response, 429, ErrorCode.RATE_LIMITED,
                "Too many requests. Please wait a moment and try again.");
    }

    private static Bucket newBucket(int perMinute) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)).build())
                .build();
    }
}

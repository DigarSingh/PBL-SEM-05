package com.shopsmart.auth;

import com.shopsmart.common.exception.ApiException;
import com.shopsmart.common.exception.ErrorCode;
import com.shopsmart.common.util.SecureTokens;
import com.shopsmart.config.AppProperties;
import com.shopsmart.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rotating refresh tokens with reuse detection. Presenting an already-rotated token after the
 * grace window means the token was probably stolen, so the whole token family is revoked.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    /** Concurrent refreshes from several tabs can legitimately present a just-rotated token. */
    static final Duration ROTATION_GRACE = Duration.ofSeconds(30);

    private final RefreshTokenRepository repository;
    private final AppProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, AppProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public String issue(User user, String ip, String userAgent) {
        return create(user, UUID.randomUUID().toString(), ip, userAgent);
    }

    /** Validates and rotates a refresh token, returning the new raw token and its owner. */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken, String ip, String userAgent) {
        Instant now = clock.instant();
        RefreshToken current = repository.findByHashWithUser(SecureTokens.sha256(rawToken))
                .orElseThrow(RefreshTokenService::invalid);
        if (current.getRevokedAt() != null) {
            if (current.getRevokedAt().isBefore(now.minus(ROTATION_GRACE))) {
                int revoked = repository.revokeFamily(current.getFamilyId(), now);
                log.warn("Refresh token reuse detected for user {}; revoked {} tokens in family",
                        current.getUser().getId(), revoked);
            }
            throw invalid();
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw invalid();
        }
        current.setRevokedAt(now);
        String next = create(current.getUser(), current.getFamilyId(), ip, userAgent);
        return new Rotation(next, current.getUser());
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findByHashWithUser(SecureTokens.sha256(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        repository.revokeAllForUser(userId, clock.instant());
    }

    private String create(User user, String familyId, String ip, String userAgent) {
        String raw = SecureTokens.generate();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(SecureTokens.sha256(raw));
        token.setFamilyId(familyId);
        token.setExpiresAt(clock.instant().plus(properties.jwt().refreshExpiration()));
        token.setCreatedByIp(ip);
        token.setUserAgent(userAgent);
        repository.save(token);
        return raw;
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_TOKEN,
                "Your session has expired. Please sign in again.");
    }

    public record Rotation(String rawToken, User user) {
    }
}

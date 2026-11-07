package com.shopsmart.auth;

import com.shopsmart.common.exception.ApiException;
import com.shopsmart.common.exception.ErrorCode;
import com.shopsmart.common.util.SecureTokens;
import com.shopsmart.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Single-use, expiring tokens for e-mail verification and password reset. */
@Service
public class UserTokenService {

    private final UserTokenRepository repository;
    private final Clock clock;

    public UserTokenService(UserTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Creates a new token, invalidating any outstanding token of the same type. */
    @Transactional
    public String issue(User user, UserTokenType type, Duration ttl) {
        Instant now = clock.instant();
        repository.invalidateOutstanding(user.getId(), type, now);
        String raw = SecureTokens.generate();
        UserToken token = new UserToken();
        token.setUser(user);
        token.setType(type);
        token.setTokenHash(SecureTokens.sha256(raw));
        token.setExpiresAt(now.plus(ttl));
        repository.save(token);
        return raw;
    }

    /** Marks the token used and returns its user; fails for unknown, used or expired tokens. */
    @Transactional
    public User consume(String rawToken, UserTokenType type) {
        Instant now = clock.instant();
        UserToken token = repository.findByHashWithUser(SecureTokens.sha256(rawToken))
                .filter(candidate -> candidate.getType() == type)
                .filter(candidate -> candidate.isUsable(now))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_TOKEN,
                        "This link is invalid or has expired. Please request a new one."));
        token.setUsedAt(now);
        return token.getUser();
    }
}

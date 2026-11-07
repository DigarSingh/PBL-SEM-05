package com.shopsmart.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Purges long-expired refresh / verification / reset tokens. */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class TokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupJob.class);
    private static final Duration RETENTION = Duration.ofDays(7);

    private final RefreshTokenRepository refreshTokens;
    private final UserTokenRepository userTokens;
    private final Clock clock;

    public TokenCleanupJob(RefreshTokenRepository refreshTokens, UserTokenRepository userTokens, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.userTokens = userTokens;
        this.clock = clock;
    }

    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        Instant cutoff = clock.instant().minus(RETENTION);
        int refresh = refreshTokens.deleteExpiredBefore(cutoff);
        int single = userTokens.deleteExpiredBefore(cutoff);
        log.info("Purged {} refresh tokens and {} single-use tokens expired before {}", refresh, single, cutoff);
    }
}

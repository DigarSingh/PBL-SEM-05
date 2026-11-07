package com.shopsmart.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    @Query("select t from UserToken t join fetch t.user where t.tokenHash = :hash")
    Optional<UserToken> findByHashWithUser(@Param("hash") String tokenHash);

    @Modifying
    @Query("""
            update UserToken t set t.usedAt = :now
            where t.user.id = :userId and t.type = :type and t.usedAt is null
            """)
    int invalidateOutstanding(@Param("userId") Long userId, @Param("type") UserTokenType type,
                              @Param("now") Instant now);

    @Modifying
    @Query("delete from UserToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}

package com.shopsmart.activity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {

    @Query("select a from UserActivity a where a.userId = :userId order by a.createdAt desc, a.id desc")
    List<UserActivity> findRecentByUser(@Param("userId") Long userId, Pageable pageable);

    @Query("select max(a.createdAt) from UserActivity a where a.userId = :userId")
    Optional<Instant> findLatestActivityAt(@Param("userId") Long userId);

    boolean existsByUserIdAndEventTypeAndProductIdAndCreatedAtAfter(Long userId, ActivityType eventType,
                                                                     Long productId, Instant after);
}

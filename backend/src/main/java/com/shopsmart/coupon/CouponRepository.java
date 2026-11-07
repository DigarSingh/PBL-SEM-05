package com.shopsmart.coupon;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.code = :code")
    Optional<Coupon> lockByCode(@Param("code") String code);

    @Query("""
            select c from Coupon c
            where (:q is null or lower(c.code) like :q or lower(c.description) like :q)
              and (:active is null or c.active = :active)
            """)
    Page<Coupon> search(@Param("q") String likePattern, @Param("active") Boolean active, Pageable pageable);

    @Query("""
            select c from Coupon c
            where c.active = true
              and (c.startsAt is null or c.startsAt <= :now)
              and (c.expiresAt is null or c.expiresAt > :now)
              and (c.usageLimit is null or c.usedCount < c.usageLimit)
            order by c.discountValue desc
            """)
    List<Coupon> findCurrentlyValid(@Param("now") Instant now);
}

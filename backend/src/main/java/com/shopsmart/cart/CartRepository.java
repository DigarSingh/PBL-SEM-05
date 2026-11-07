package com.shopsmart.cart;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<Cart, Long> {

    @Query("""
            select distinct c from Cart c
            left join fetch c.items i left join fetch i.variant v left join fetch v.product p
            left join fetch p.brand left join fetch p.category
            where c.user.id = :userId
            """)
    Optional<Cart> findWithItemsByUserId(@Param("userId") Long userId);

    /** Serialises checkouts of the same cart (e.g. two browser tabs). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.user.id = :userId")
    Optional<Cart> lockByUserId(@Param("userId") Long userId);
}

package com.shopsmart.wishlist;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    @Query("""
            select distinct w from Wishlist w
            left join fetch w.items i left join fetch i.product p left join fetch p.brand left join fetch p.category
            where w.user.id = :userId
            """)
    Optional<Wishlist> findWithItemsByUserId(@Param("userId") Long userId);
}

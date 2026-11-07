package com.shopsmart.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query("select m from StockMovement m where m.variant.id = :variantId order by m.createdAt desc, m.id desc")
    Page<StockMovement> findByVariantId(@Param("variantId") Long variantId, Pageable pageable);
}

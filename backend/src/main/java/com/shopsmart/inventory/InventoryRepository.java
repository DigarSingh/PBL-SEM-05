package com.shopsmart.inventory;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Team Member 1 — Backend Core & Concurrency Lead
 * <p>
 * Inventory data access with pessimistic database locking:
 * <ul>
 *   <li>Pessimistic write locking ({@code SELECT ... FOR UPDATE})</li>
 *   <li>Deadlock-free inventory locking via ascending variant ID sort order</li>
 *   <li>Real-time stock validation and threshold monitoring queries</li>
 * </ul>
 */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * Locks inventory rows (SELECT ... FOR UPDATE) in variant-id order. A consistent lock order
     * prevents deadlocks between concurrent checkouts touching overlapping variants.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.variant.id in :variantIds order by i.variant.id")
    List<Inventory> lockByVariantIds(@Param("variantIds") Collection<Long> variantIds);

    @Query("select i from Inventory i join fetch i.variant v where v.id in :variantIds")
    List<Inventory> findByVariantIds(@Param("variantIds") Collection<Long> variantIds);

    @Query("""
            select i from Inventory i join fetch i.variant v join fetch v.product p
            where p.id in :productIds and v.active = true
            """)
    List<Inventory> findActiveByProductIds(@Param("productIds") Collection<Long> productIds);

    @Query("select i from Inventory i where i.variant.id = :variantId")
    Optional<Inventory> findByVariantId(@Param("variantId") Long variantId);

    @Query(value = """
            select i from Inventory i join fetch i.variant v join fetch v.product p join fetch p.category c
            where p.status <> com.shopsmart.product.ProductStatus.ARCHIVED
              and (:q is null or lower(p.name) like :q or lower(v.sku) like :q or lower(v.name) like :q)
              and (:categoryId is null or c.id = :categoryId)
              and (:lowStock = false or (i.quantity - i.reserved) <= i.lowStockThreshold)
              and (:outOfStock = false or (i.quantity - i.reserved) <= 0)
            """,
            countQuery = """
            select count(i) from Inventory i join i.variant v join v.product p join p.category c
            where p.status <> com.shopsmart.product.ProductStatus.ARCHIVED
              and (:q is null or lower(p.name) like :q or lower(v.sku) like :q or lower(v.name) like :q)
              and (:categoryId is null or c.id = :categoryId)
              and (:lowStock = false or (i.quantity - i.reserved) <= i.lowStockThreshold)
              and (:outOfStock = false or (i.quantity - i.reserved) <= 0)
            """)
    Page<Inventory> search(@Param("q") String likePattern, @Param("categoryId") Long categoryId,
                           @Param("lowStock") boolean lowStock, @Param("outOfStock") boolean outOfStock,
                           Pageable pageable);

    @Query("""
            select count(i) from Inventory i join i.variant v join v.product p
            where v.active = true and p.status = com.shopsmart.product.ProductStatus.ACTIVE
              and (i.quantity - i.reserved) <= i.lowStockThreshold
            """)
    long countLowStock();

    @Query("""
            select i from Inventory i join fetch i.variant v join fetch v.product p
            where v.active = true and p.status = com.shopsmart.product.ProductStatus.ACTIVE
              and (i.quantity - i.reserved) <= i.lowStockThreshold
            order by (i.quantity - i.reserved) asc, p.name asc
            """)
    List<Inventory> findLowStock(Pageable pageable);
}

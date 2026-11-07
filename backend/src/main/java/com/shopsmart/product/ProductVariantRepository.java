package com.shopsmart.product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    Optional<ProductVariant> findBySku(String sku);

    @Query("select v.sku from ProductVariant v where v.sku in :skus and v.product.id <> :productId")
    List<String> findSkusUsedByOtherProducts(@Param("skus") Collection<String> skus, @Param("productId") Long productId);

    @Query("select v.sku from ProductVariant v where v.sku in :skus")
    List<String> findExistingSkus(@Param("skus") Collection<String> skus);

    @Query("select v from ProductVariant v join fetch v.product p join fetch p.brand join fetch p.category where v.id = :id")
    Optional<ProductVariant> findWithProduct(@Param("id") Long id);

    @Query("select v from ProductVariant v join fetch v.product p where v.id in :ids")
    List<ProductVariant> findAllWithProduct(@Param("ids") Collection<Long> ids);

    @Query("select count(oi) > 0 from OrderItem oi where oi.variantId = :variantId")
    boolean hasOrders(@Param("variantId") Long variantId);
}

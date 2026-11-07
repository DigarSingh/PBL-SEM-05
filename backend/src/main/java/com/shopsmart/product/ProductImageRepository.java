package com.shopsmart.product;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    @Query("select i from ProductImage i where i.product.id in :productIds order by i.product.id, i.sortOrder, i.id")
    List<ProductImage> findByProductIds(@Param("productIds") Collection<Long> productIds);
}

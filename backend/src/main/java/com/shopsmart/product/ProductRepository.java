package com.shopsmart.product;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySlug(String slug);

    @Query("select p from Product p join fetch p.brand join fetch p.category where p.id = :id")
    Optional<Product> findWithBrandAndCategory(@Param("id") Long id);

    @Query("select p from Product p join fetch p.brand join fetch p.category where p.slug = :slug")
    Optional<Product> findWithBrandAndCategoryBySlug(@Param("slug") String slug);

    @Query("select p from Product p join fetch p.brand join fetch p.category where p.id in :ids")
    List<Product> findAllWithBrandAndCategory(@Param("ids") Collection<Long> ids);

    @Query("select p from Product p where p.brand.id = :brandId")
    List<Product> findByBrandId(@Param("brandId") Long brandId);

    @Query("select p from Product p where p.category.id = :categoryId")
    List<Product> findByCategoryId(@Param("categoryId") Long categoryId);

    long countByStatus(ProductStatus status);

    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.soldCount = case when p.soldCount + :delta < 0 then 0 else p.soldCount + :delta end where p.id = :id")
    int incrementSoldCount(@Param("id") Long id, @Param("delta") int delta);

    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.averageRating = :average, p.reviewCount = :count where p.id = :id")
    int updateRating(@Param("id") Long id, @Param("average") BigDecimal average, @Param("count") int count);
}

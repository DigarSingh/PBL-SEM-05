package com.shopsmart.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);

    Optional<Category> findBySlug(String slug);

    Optional<Category> findBySlugAndActiveTrue(String slug);

    List<Category> findAllByOrderBySortOrderAscNameAsc();

    List<Category> findByActiveTrueOrderBySortOrderAscNameAsc();

    @Query("""
            select p.category.id, count(p) from Product p
            where p.status = com.shopsmart.product.ProductStatus.ACTIVE and p.brand.active = true
            group by p.category.id
            """)
    List<Object[]> countActiveProductsByCategory();

    @Query("select count(p) from Product p where p.category.id = :categoryId")
    long countProducts(Long categoryId);
}

package com.shopsmart.brand;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    boolean existsBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);

    Optional<Brand> findBySlug(String slug);

    List<Brand> findAllByOrderByNameAsc();

    List<Brand> findByActiveTrueOrderByNameAsc();

    @Query("""
            select p.brand.id, count(p) from Product p
            where p.status = com.shopsmart.product.ProductStatus.ACTIVE and p.category.active = true
            group by p.brand.id
            """)
    List<Object[]> countActiveProductsByBrand();

    @Query("select count(p) from Product p where p.brand.id = :brandId")
    long countProducts(Long brandId);
}

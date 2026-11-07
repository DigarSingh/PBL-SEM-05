package com.shopsmart.review;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query(value = "select r from Review r join fetch r.user where r.product.id = :productId and r.status = :status",
            countQuery = "select count(r) from Review r where r.product.id = :productId and r.status = :status")
    Page<Review> findByProduct(@Param("productId") Long productId, @Param("status") ReviewStatus status,
                               Pageable pageable);

    Optional<Review> findByUserIdAndProductId(Long userId, Long productId);

    @Query("select avg(r.rating), count(r) from Review r where r.product.id = :productId and r.status = com.shopsmart.review.ReviewStatus.PUBLISHED")
    List<Object[]> aggregate(@Param("productId") Long productId);

    @Query("""
            select r.rating, count(r) from Review r
            where r.product.id = :productId and r.status = com.shopsmart.review.ReviewStatus.PUBLISHED
            group by r.rating
            """)
    List<Object[]> distribution(@Param("productId") Long productId);

    @Query(value = """
            select r from Review r join fetch r.user u join fetch r.product p
            where (:status is null or r.status = :status)
              and (:rating is null or r.rating = :rating)
              and (:productId is null or p.id = :productId)
              and (:q is null or lower(r.title) like :q or lower(r.comment) like :q or lower(p.name) like :q
                   or lower(u.email) like :q)
            """,
            countQuery = """
            select count(r) from Review r join r.user u join r.product p
            where (:status is null or r.status = :status)
              and (:rating is null or r.rating = :rating)
              and (:productId is null or p.id = :productId)
              and (:q is null or lower(r.title) like :q or lower(r.comment) like :q or lower(p.name) like :q
                   or lower(u.email) like :q)
            """)
    Page<Review> search(@Param("status") ReviewStatus status, @Param("rating") Integer rating,
                        @Param("productId") Long productId, @Param("q") String likePattern, Pageable pageable);
}

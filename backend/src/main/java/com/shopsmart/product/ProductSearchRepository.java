package com.shopsmart.product;

import com.shopsmart.brand.Brand;
import com.shopsmart.category.Category;
import com.shopsmart.inventory.Inventory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Criteria-API product search (keyword layer + structured filters) with database-side
 * paging and relevance ordering.
 */
@Repository
public class ProductSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public Page<Product> search(ProductSearchCriteria criteria, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Product> query = cb.createQuery(Product.class);
        Root<Product> product = query.from(Product.class);
        Join<Product, Brand> brand = (Join<Product, Brand>) product.<Product, Brand>fetch("brand", JoinType.INNER);
        Join<Product, Category> category = (Join<Product, Category>) product.<Product, Category>fetch("category",
                JoinType.INNER);
        query.select(product)
                .where(predicates(cb, query, product, brand, category, criteria))
                .orderBy(orderBy(cb, product, criteria));
        List<Product> content = entityManager.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Product> countRoot = countQuery.from(Product.class);
        Join<Product, Brand> countBrand = countRoot.join("brand");
        Join<Product, Category> countCategory = countRoot.join("category");
        countQuery.select(cb.count(countRoot))
                .where(predicates(cb, countQuery, countRoot, countBrand, countCategory, criteria));
        long total = entityManager.createQuery(countQuery).getSingleResult();
        return new PageImpl<>(content, pageable, total);
    }

    /** Ids only, in ranking order: used to build candidate sets for semantic ranking. */
    public List<Long> searchIds(ProductSearchCriteria criteria, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Product> product = query.from(Product.class);
        Join<Product, Brand> brand = product.join("brand");
        Join<Product, Category> category = product.join("category");
        query.select(product.get("id"))
                .where(predicates(cb, query, product, brand, category, criteria))
                .orderBy(orderBy(cb, product, criteria));
        return entityManager.createQuery(query).setMaxResults(limit).getResultList();
    }

    /** Brand facet counts and the price range for the given filters (the brand filter itself is ignored). */
    public Facets facets(ProductSearchCriteria criteria) {
        ProductSearchCriteria withoutBrand = criteria.toBuilder().brandSlugs(null).brandId(null).build();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Tuple> brandQuery = cb.createTupleQuery();
        Root<Product> product = brandQuery.from(Product.class);
        Join<Product, Brand> brand = product.join("brand");
        Join<Product, Category> category = product.join("category");
        brandQuery.multiselect(brand.get("id"), brand.get("name"), brand.get("slug"), cb.count(product))
                .where(predicates(cb, brandQuery, product, brand, category, withoutBrand))
                .groupBy(brand.get("id"), brand.get("name"), brand.get("slug"))
                .orderBy(cb.asc(brand.get("name")));
        List<BrandFacet> brands = entityManager.createQuery(brandQuery).getResultList().stream()
                .map(row -> new BrandFacet(row.get(0, Long.class), row.get(1, String.class), row.get(2, String.class),
                        row.get(3, Long.class)))
                .toList();

        CriteriaQuery<Tuple> priceQuery = cb.createTupleQuery();
        Root<Product> priceRoot = priceQuery.from(Product.class);
        Join<Product, Brand> priceBrand = priceRoot.join("brand");
        Join<Product, Category> priceCategory = priceRoot.join("category");
        ProductSearchCriteria withoutPrice = criteria.toBuilder().minPrice(null).maxPrice(null).build();
        priceQuery.multiselect(cb.min(priceRoot.get("salePrice")), cb.max(priceRoot.get("salePrice")))
                .where(predicates(cb, priceQuery, priceRoot, priceBrand, priceCategory, withoutPrice));
        Tuple range = entityManager.createQuery(priceQuery).getSingleResult();
        return new Facets(brands, range.get(0, BigDecimal.class), range.get(1, BigDecimal.class));
    }

    private Predicate[] predicates(CriteriaBuilder cb, AbstractQuery<?> query, Root<Product> product,
                                   Join<Product, Brand> brand, Join<Product, Category> category,
                                   ProductSearchCriteria criteria) {
        List<Predicate> predicates = new ArrayList<>();
        if (criteria.statuses() != null && !criteria.statuses().isEmpty()) {
            predicates.add(product.get("status").in(criteria.statuses()));
        }
        if (criteria.storefront()) {
            predicates.add(cb.isTrue(category.get("active")));
            predicates.add(cb.isTrue(brand.get("active")));
        }
        if (criteria.categoryId() != null) {
            predicates.add(cb.equal(category.get("id"), criteria.categoryId()));
        }
        if (criteria.categorySlug() != null) {
            predicates.add(cb.equal(category.get("slug"), criteria.categorySlug()));
        }
        if (criteria.brandSlugs() != null && !criteria.brandSlugs().isEmpty()) {
            predicates.add(brand.get("slug").in(criteria.brandSlugs()));
        }
        if (criteria.brandId() != null) {
            predicates.add(cb.equal(brand.get("id"), criteria.brandId()));
        }
        if (criteria.minPrice() != null) {
            predicates.add(cb.greaterThanOrEqualTo(product.get("salePrice"), criteria.minPrice()));
        }
        if (criteria.maxPrice() != null) {
            predicates.add(cb.lessThanOrEqualTo(product.get("salePrice"), criteria.maxPrice()));
        }
        if (criteria.minRating() != null) {
            predicates.add(cb.greaterThanOrEqualTo(product.get("averageRating"), criteria.minRating()));
        }
        if (criteria.minDiscount() != null) {
            predicates.add(cb.greaterThanOrEqualTo(product.get("discountPercent"), criteria.minDiscount()));
        }
        if (criteria.featured() != null) {
            predicates.add(cb.equal(product.get("featured"), criteria.featured()));
        }
        if (criteria.productIds() != null) {
            predicates.add(criteria.productIds().isEmpty() ? cb.disjunction()
                    : product.get("id").in(criteria.productIds()));
        }
        if (criteria.excludeIds() != null && !criteria.excludeIds().isEmpty()) {
            predicates.add(cb.not(product.get("id").in(criteria.excludeIds())));
        }
        if (criteria.inStockOnly()) {
            Subquery<Long> stock = query.subquery(Long.class);
            Root<Inventory> inventory = stock.from(Inventory.class);
            Join<Inventory, ProductVariant> variant = inventory.join("variant");
            stock.select(inventory.get("id")).where(
                    cb.equal(variant.get("product"), product),
                    cb.isTrue(variant.get("active")),
                    cb.gt(cb.diff(inventory.<Integer>get("quantity"), inventory.<Integer>get("reserved")), 0));
            predicates.add(cb.exists(stock));
        }
        Expression<String> searchText = product.get("searchText");
        for (List<String> group : KeywordQuery.termGroups(criteria.keywords())) {
            predicates.add(cb.or(group.stream()
                    .map(term -> cb.like(searchText, "%" + escapeLike(term) + "%", '\\'))
                    .toArray(Predicate[]::new)));
        }
        return predicates.toArray(Predicate[]::new);
    }

    private List<Order> orderBy(CriteriaBuilder cb, Root<Product> product, ProductSearchCriteria criteria) {
        List<Order> orders = new ArrayList<>();
        ProductSort sort = criteria.sort() == null ? ProductSort.POPULARITY : criteria.sort();
        List<String> tokens = KeywordQuery.tokens(criteria.keywords());
        if (sort == ProductSort.RELEVANCE && !tokens.isEmpty()) {
            Expression<String> name = cb.lower(product.get("name"));
            String phrase = "%" + escapeLike(String.join(" ", tokens)) + "%";
            String first = "%" + escapeLike(tokens.getFirst()) + "%";
            orders.add(cb.asc(cb.selectCase()
                    .when(cb.like(name, phrase, '\\'), 0)
                    .when(cb.like(name, first, '\\'), 1)
                    .otherwise(2)));
            sort = ProductSort.POPULARITY;
        } else if (sort == ProductSort.RELEVANCE) {
            sort = ProductSort.POPULARITY;
        }
        switch (sort) {
            case NEWEST -> orders.add(cb.desc(product.get("createdAt")));
            case PRICE_ASC -> orders.add(cb.asc(product.get("salePrice")));
            case PRICE_DESC -> orders.add(cb.desc(product.get("salePrice")));
            case RATING -> {
                orders.add(cb.desc(product.get("averageRating")));
                orders.add(cb.desc(product.get("reviewCount")));
            }
            case DISCOUNT -> {
                orders.add(cb.desc(product.get("discountPercent")));
                orders.add(cb.desc(product.get("soldCount")));
            }
            default -> {
                orders.add(cb.desc(product.get("soldCount")));
                orders.add(cb.desc(product.get("averageRating")));
            }
        }
        orders.add(cb.desc(product.get("id")));
        return orders;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public record BrandFacet(Long id, String name, String slug, long count) {
    }

    public record Facets(List<BrandFacet> brands, BigDecimal minPrice, BigDecimal maxPrice) {
    }
}

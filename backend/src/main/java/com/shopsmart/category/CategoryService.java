package com.shopsmart.category;

import com.shopsmart.admin.AuditService;
import com.shopsmart.category.dto.CategoryRequest;
import com.shopsmart.category.dto.CategoryResponse;
import com.shopsmart.common.exception.ApiException;
import com.shopsmart.common.util.Slugs;
import com.shopsmart.product.events.CatalogNameChangedEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CategoryService {

    private final CategoryRepository repository;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    public CategoryService(CategoryRepository repository, AuditService auditService,
                        ApplicationEventPublisher events) {
        this.repository = repository;
        this.auditService = auditService;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listActive() {
        Map<Long, Long> counts = productCounts();
        return repository.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(category -> toResponse(category, counts.getOrDefault(category.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        Map<Long, Long> counts = productCounts();
        return repository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(category -> toResponse(category, counts.getOrDefault(category.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getActiveBySlug(String slug) {
        Category category = repository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> ApiException.notFound("Category"));
        return toResponse(category, productCounts().getOrDefault(category.getId(), 0L));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (repository.existsByNameIgnoreCase(request.name().trim())) {
            throw ApiException.duplicate("A category with this name already exists");
        }
        Category category = new Category();
        apply(category, request);
        repository.save(category);
        auditService.record("CATEGORY_CREATED", "CATEGORY", category.getId(), Map.of("name", category.getName()));
        return toResponse(category, 0);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = load(id);
        String previousName = category.getName();
        if (!category.getName().equalsIgnoreCase(request.name().trim())
                && repository.existsByNameIgnoreCase(request.name().trim())) {
            throw ApiException.duplicate("A category with this name already exists");
        }
        apply(category, request);
        if (!previousName.equals(category.getName())) {
            events.publishEvent(new CatalogNameChangedEvent(null, id));
        }
        auditService.record("CATEGORY_UPDATED", "CATEGORY", id, Map.of("name", category.getName(),
                "active", category.isActive()));
        return toResponse(category, repository.countProducts(id));
    }

    /** Categories with products cannot be deleted; deactivating hides them and their products. */
    @Transactional
    public void delete(Long id) {
        Category category = load(id);
        if (repository.countProducts(id) > 0) {
            throw ApiException.conflict("This category has products. Deactivate it instead of deleting it.");
        }
        repository.delete(category);
        auditService.record("CATEGORY_DELETED", "CATEGORY", id, Map.of("name", category.getName()));
    }

    public Category load(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
    }

    private void apply(Category category, CategoryRequest request) {
        category.setName(request.name().trim());
        String requestedSlug = StringUtils.hasText(request.slug()) ? request.slug() : Slugs.slugify(request.name());
        if (!requestedSlug.equals(category.getSlug())) {
            category.setSlug(Slugs.unique(requestedSlug, repository::existsBySlug));
        }
        category.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : null);
        category.setImageUrl(StringUtils.hasText(request.imageUrl()) ? request.imageUrl().trim() : null);
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            category.setActive(request.active());
        }
    }

    private Map<Long, Long> productCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : repository.countActiveProductsByCategory()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private static CategoryResponse toResponse(Category category, long productCount) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSlug(),
                category.getDescription(), category.getImageUrl(), category.getSortOrder(), category.isActive(),
                productCount);
    }
}

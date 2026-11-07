package com.shopsmart.brand;

import com.shopsmart.admin.AuditService;
import com.shopsmart.brand.dto.BrandRequest;
import com.shopsmart.brand.dto.BrandResponse;
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
public class BrandService {

    private final BrandRepository repository;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    public BrandService(BrandRepository repository, AuditService auditService,
                        ApplicationEventPublisher events) {
        this.repository = repository;
        this.auditService = auditService;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<BrandResponse> listActive() {
        Map<Long, Long> counts = productCounts();
        return repository.findByActiveTrueOrderByNameAsc().stream()
                .map(brand -> toResponse(brand, counts.getOrDefault(brand.getId(), 0L)))
                .filter(response -> response.productCount() > 0)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BrandResponse> listAll() {
        Map<Long, Long> counts = productCounts();
        return repository.findAllByOrderByNameAsc().stream()
                .map(brand -> toResponse(brand, counts.getOrDefault(brand.getId(), 0L)))
                .toList();
    }

    @Transactional
    public BrandResponse create(BrandRequest request) {
        if (repository.existsByNameIgnoreCase(request.name().trim())) {
            throw ApiException.duplicate("A brand with this name already exists");
        }
        Brand brand = new Brand();
        apply(brand, request);
        repository.save(brand);
        auditService.record("BRAND_CREATED", "BRAND", brand.getId(), Map.of("name", brand.getName()));
        return toResponse(brand, 0);
    }

    @Transactional
    public BrandResponse update(Long id, BrandRequest request) {
        Brand brand = load(id);
        String previousName = brand.getName();
        if (!brand.getName().equalsIgnoreCase(request.name().trim())
                && repository.existsByNameIgnoreCase(request.name().trim())) {
            throw ApiException.duplicate("A brand with this name already exists");
        }
        apply(brand, request);
        if (!previousName.equals(brand.getName())) {
            events.publishEvent(new CatalogNameChangedEvent(id, null));
        }
        auditService.record("BRAND_UPDATED", "BRAND", brand.getId(), Map.of("name", brand.getName()));
        return toResponse(brand, repository.countProducts(id));
    }

    /** Brands with products cannot be deleted (orders reference them); deactivate instead. */
    @Transactional
    public void delete(Long id) {
        Brand brand = load(id);
        if (repository.countProducts(id) > 0) {
            throw ApiException.conflict("This brand has products. Deactivate it instead of deleting it.");
        }
        repository.delete(brand);
        auditService.record("BRAND_DELETED", "BRAND", id, Map.of("name", brand.getName()));
    }

    public Brand load(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Brand"));
    }

    private void apply(Brand brand, BrandRequest request) {
        brand.setName(request.name().trim());
        String requestedSlug = StringUtils.hasText(request.slug()) ? request.slug() : Slugs.slugify(request.name());
        if (!requestedSlug.equals(brand.getSlug())) {
            brand.setSlug(Slugs.unique(requestedSlug, repository::existsBySlug));
        }
        brand.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : null);
        brand.setLogoUrl(StringUtils.hasText(request.logoUrl()) ? request.logoUrl().trim() : null);
        if (request.active() != null) {
            brand.setActive(request.active());
        }
    }

    private Map<Long, Long> productCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : repository.countActiveProductsByBrand()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private static BrandResponse toResponse(Brand brand, long productCount) {
        return new BrandResponse(brand.getId(), brand.getName(), brand.getSlug(), brand.getDescription(),
                brand.getLogoUrl(), brand.isActive(), productCount);
    }
}

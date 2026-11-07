package com.shopsmart.inventory;

import com.shopsmart.admin.AuditService;
import com.shopsmart.common.api.PageResponse;
import com.shopsmart.common.exception.ApiException;
import com.shopsmart.inventory.dto.InventoryItemResponse;
import com.shopsmart.inventory.dto.StockAdjustmentRequest;
import com.shopsmart.inventory.dto.StockMovementResponse;
import com.shopsmart.inventory.events.LowStockEvent;
import com.shopsmart.product.Product;
import com.shopsmart.product.ProductVariant;
import com.shopsmart.product.StockStatus;
import com.shopsmart.security.SecurityUtils;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Team Member 1 — Backend Core & Concurrency Lead
 * <p>
 * Stock management and overselling prevention:
 * <ul>
 *   <li>Inventory validation and atomic stock reservation/sales</li>
 *   <li>Guaranteed inventory consistency under concurrent checkouts</li>
 *   <li>Audit trail for every mutation via {@link StockMovement}</li>
 *   <li>Real-time {@link LowStockEvent} notification on crossing thresholds</li>
 * </ul>
 * <p>
 * The only place stock changes. Every mutation writes a {@link StockMovement} and publishes a
 * {@link LowStockEvent} when availability crosses the variant's threshold. Callers that change
 * stock during checkout must first lock the rows with {@link #lockForVariants}.
 */
@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository movementRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    public InventoryService(InventoryRepository inventoryRepository, StockMovementRepository movementRepository,
                            AuditService auditService, ApplicationEventPublisher events) {
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.auditService = auditService;
        this.events = events;
    }

    @Transactional
    public Inventory createForVariant(ProductVariant variant, int initialQuantity, int lowStockThreshold) {
        Inventory inventory = new Inventory();
        inventory.setVariant(variant);
        inventory.setQuantity(initialQuantity);
        inventory.setReserved(0);
        inventory.setLowStockThreshold(lowStockThreshold);
        inventoryRepository.save(inventory);
        if (initialQuantity > 0) {
            record(inventory, StockMovementType.INITIAL, initialQuantity, 0, null, "Initial stock");
        }
        return inventory;
    }

    /** Locks the inventory rows of the given variants for the rest of the transaction. */
    @Transactional
    public Map<Long, Inventory> lockForVariants(Collection<Long> variantIds) {
        Map<Long, Inventory> locked = new LinkedHashMap<>();
        for (Inventory inventory : inventoryRepository.lockByVariantIds(variantIds)) {
            locked.put(inventory.getVariant().getId(), inventory);
        }
        return locked;
    }

    public void sell(Inventory inventory, int units, Long orderId) {
        boolean wasLow = inventory.isLowStock();
        inventory.deduct(units);
        record(inventory, StockMovementType.SALE, -units, 0, orderId, null);
        publishIfBecameLow(inventory, wasLow);
    }

    public void reserve(Inventory inventory, int units, Long orderId) {
        boolean wasLow = inventory.isLowStock();
        inventory.reserve(units);
        record(inventory, StockMovementType.RESERVATION, 0, units, orderId, null);
        publishIfBecameLow(inventory, wasLow);
    }

    public void commitReservation(Inventory inventory, int units, Long orderId) {
        inventory.commitReservation(units);
        record(inventory, StockMovementType.RESERVATION_COMMITTED, -units, -units, orderId, null);
    }

    public void releaseReservation(Inventory inventory, int units, Long orderId) {
        int before = inventory.getReserved();
        inventory.releaseReservation(units);
        record(inventory, StockMovementType.RESERVATION_RELEASED, 0, inventory.getReserved() - before, orderId, null);
    }

    public void restockCancelled(Inventory inventory, int units, Long orderId) {
        inventory.restock(units);
        record(inventory, StockMovementType.CANCELLATION_RESTOCK, units, 0, orderId, "Order cancelled");
    }

    /** Administrator stock change (restock or correction), serialised with checkouts by a row lock. */
    @Transactional
    public InventoryItemResponse adjust(Long variantId, StockAdjustmentRequest request) {
        if (request.quantity() == 0) {
            throw ApiException.badRequest("Quantity must not be zero");
        }
        if (request.type() == StockAdjustmentRequest.Type.RESTOCK && request.quantity() < 0) {
            throw ApiException.badRequest("A restock must add a positive quantity");
        }
        Inventory inventory = lockForVariants(java.util.List.of(variantId)).get(variantId);
        if (inventory == null) {
            throw ApiException.notFound("Inventory for variant " + variantId);
        }
        boolean wasLow = inventory.isLowStock();
        try {
            inventory.adjust(request.quantity());
        } catch (IllegalStateException e) {
            throw ApiException.businessRule(e.getMessage());
        }
        StockMovementType type = request.type() == StockAdjustmentRequest.Type.RESTOCK
                ? StockMovementType.RESTOCK : StockMovementType.ADJUSTMENT;
        String note = StringUtils.hasText(request.note()) ? request.note().trim() : null;
        record(inventory, type, request.quantity(), 0, null, note);
        publishIfBecameLow(inventory, wasLow);
        auditService.record("STOCK_" + type.name(), "VARIANT", variantId, Map.of(
                "change", request.quantity(), "quantityAfter", inventory.getQuantity(),
                "note", note == null ? "" : note));
        return toResponse(inventory);
    }

    @Transactional
    public InventoryItemResponse updateThreshold(Long variantId, int threshold) {
        Inventory inventory = inventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> ApiException.notFound("Inventory for variant " + variantId));
        boolean wasLow = inventory.isLowStock();
        inventory.setLowStockThreshold(threshold);
        publishIfBecameLow(inventory, wasLow);
        return toResponse(inventory);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryItemResponse> search(String q, Long categoryId, boolean lowStock,
                                                      boolean outOfStock, int page, int size) {
        String pattern = StringUtils.hasText(q) ? "%" + q.trim().toLowerCase(Locale.ROOT) + "%" : null;
        Pageable pageable = PageRequest.of(page, size, Sort.by("p.name").ascending().and(Sort.by("v.sortOrder")));
        return PageResponse.from(inventoryRepository.search(pattern, categoryId, lowStock, outOfStock, pageable),
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> movements(Long variantId, int page, int size) {
        return PageResponse.from(movementRepository.findByVariantId(variantId, PageRequest.of(page, size)),
                movement -> new StockMovementResponse(movement.getId(), movement.getType(),
                        movement.getQuantityChange(), movement.getReservedChange(), movement.getQuantityAfter(),
                        movement.getReservedAfter(), movement.getOrderId(), movement.getActorUserId(),
                        movement.getNote(), movement.getCreatedAt()));
    }

    private void record(Inventory inventory, StockMovementType type, int quantityChange, int reservedChange,
                        Long orderId, String note) {
        StockMovement movement = new StockMovement();
        movement.setVariant(inventory.getVariant());
        movement.setType(type);
        movement.setQuantityChange(quantityChange);
        movement.setReservedChange(reservedChange);
        movement.setQuantityAfter(inventory.getQuantity());
        movement.setReservedAfter(inventory.getReserved());
        movement.setOrderId(orderId);
        movement.setActorUserId(SecurityUtils.currentUserId().orElse(null));
        movement.setNote(note);
        movementRepository.save(movement);
    }

    private void publishIfBecameLow(Inventory inventory, boolean wasLow) {
        if (!wasLow && inventory.isLowStock()) {
            ProductVariant variant = inventory.getVariant();
            Product product = variant.getProduct();
            events.publishEvent(new LowStockEvent(variant.getId(), product.getId(), product.getName(),
                    variant.getSku(), inventory.available(), inventory.getLowStockThreshold()));
        }
    }

    public InventoryItemResponse toResponse(Inventory inventory) {
        ProductVariant variant = inventory.getVariant();
        Product product = variant.getProduct();
        return new InventoryItemResponse(variant.getId(), variant.getSku(), variant.getName(), product.getId(),
                product.getName(), product.getCategory().getName(), variant.isActive(), inventory.getQuantity(),
                inventory.getReserved(), inventory.available(), inventory.getLowStockThreshold(),
                StockStatus.of(inventory.available(), inventory.getLowStockThreshold()), inventory.getUpdatedAt());
    }
}

package com.shopsmart.admin;

import com.shopsmart.admin.dto.PublicSettingsResponse;
import com.shopsmart.admin.dto.StoreSettingsRequest;
import com.shopsmart.admin.dto.StoreSettingsResponse;
import com.shopsmart.common.util.Money;
import com.shopsmart.config.AppProperties;
import com.shopsmart.security.SecurityUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreSettingsService {

    private final StoreSettingsRepository repository;
    private final AppProperties properties;
    private final AuditService auditService;

    public StoreSettingsService(StoreSettingsRepository repository, AppProperties properties,
                                AuditService auditService) {
        this.repository = repository;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public StoreSettings current() {
        return repository.findById(StoreSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("store_settings row missing (migration V1)"));
    }

    /** Online payments are offered only when enabled by an admin AND gateway credentials exist. */
    public boolean onlinePaymentsAvailable(StoreSettings settings) {
        return settings.isRazorpayEnabled() && properties.razorpay().configured();
    }

    @Transactional(readOnly = true)
    public StoreSettingsResponse get() {
        return toResponse(current());
    }

    @Transactional(readOnly = true)
    public PublicSettingsResponse publicSettings() {
        StoreSettings settings = current();
        boolean online = onlinePaymentsAvailable(settings);
        return new PublicSettingsResponse(settings.getStoreName(), settings.getSupportEmail(),
                settings.getCurrency(), settings.getTaxRate(), settings.isPricesIncludeTax(),
                settings.getShippingFee(), settings.getFreeShippingThreshold(), settings.isCodEnabled(),
                settings.getCodMaxOrderAmount(), online, online ? properties.razorpay().keyId() : null);
    }

    @Transactional
    public StoreSettingsResponse update(StoreSettingsRequest request) {
        StoreSettings settings = current();
        Map<String, Object> changes = new LinkedHashMap<>();
        track(changes, "taxRate", settings.getTaxRate(), request.taxRate());
        track(changes, "shippingFee", settings.getShippingFee(), request.shippingFee());
        track(changes, "freeShippingThreshold", settings.getFreeShippingThreshold(), request.freeShippingThreshold());
        track(changes, "codEnabled", settings.isCodEnabled(), request.codEnabled());
        track(changes, "razorpayEnabled", settings.isRazorpayEnabled(), request.razorpayEnabled());

        settings.setStoreName(request.storeName().trim());
        settings.setSupportEmail(request.supportEmail().trim());
        settings.setTaxRate(request.taxRate());
        settings.setPricesIncludeTax(request.pricesIncludeTax());
        settings.setShippingFee(Money.of(request.shippingFee()));
        settings.setFreeShippingThreshold(Money.of(request.freeShippingThreshold()));
        settings.setDefaultLowStockThreshold(request.defaultLowStockThreshold());
        settings.setCodEnabled(request.codEnabled());
        settings.setCodMaxOrderAmount(request.codMaxOrderAmount() == null ? null : Money.of(request.codMaxOrderAmount()));
        settings.setRazorpayEnabled(request.razorpayEnabled());
        settings.setPendingPaymentTimeoutMinutes(request.pendingPaymentTimeoutMinutes());
        settings.setUpdatedByUserId(SecurityUtils.currentUserId().orElse(null));
        repository.flush();
        auditService.record("SETTINGS_UPDATED", "STORE_SETTINGS", settings.getId(), changes);
        return toResponse(settings);
    }

    private StoreSettingsResponse toResponse(StoreSettings settings) {
        return new StoreSettingsResponse(settings.getStoreName(), settings.getSupportEmail(), settings.getCurrency(),
                settings.getTaxRate(), settings.isPricesIncludeTax(), settings.getShippingFee(),
                settings.getFreeShippingThreshold(), settings.getDefaultLowStockThreshold(), settings.isCodEnabled(),
                settings.getCodMaxOrderAmount(), settings.isRazorpayEnabled(), properties.razorpay().configured(),
                settings.getPendingPaymentTimeoutMinutes(), settings.getUpdatedAt());
    }

    private static void track(Map<String, Object> changes, String field, Object before, Object after) {
        if (before == null ? after != null : !before.equals(after) && !(before instanceof java.math.BigDecimal b
                && after instanceof java.math.BigDecimal a && b.compareTo(a) == 0)) {
            changes.put(field, Map.of("from", String.valueOf(before), "to", String.valueOf(after)));
        }
    }
}

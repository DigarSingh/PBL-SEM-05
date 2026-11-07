package com.shopsmart.cart;

import com.shopsmart.activity.ActivityService;
import com.shopsmart.activity.ActivityType;
import com.shopsmart.admin.StoreSettingsService;
import com.shopsmart.cart.dto.CartItemResponse;
import com.shopsmart.cart.dto.CartResponse;
import com.shopsmart.common.exception.ApiException;
import com.shopsmart.common.exception.ErrorCode;
import com.shopsmart.common.util.Money;
import com.shopsmart.coupon.CouponEvaluation;
import com.shopsmart.coupon.CouponService;
import com.shopsmart.inventory.Inventory;
import com.shopsmart.inventory.InventoryRepository;
import com.shopsmart.pricing.PriceQuote;
import com.shopsmart.pricing.PricingLine;
import com.shopsmart.pricing.PricingService;
import com.shopsmart.product.Product;
import com.shopsmart.product.ProductImage;
import com.shopsmart.product.ProductImageRepository;
import com.shopsmart.product.ProductService;
import com.shopsmart.product.ProductVariant;
import com.shopsmart.product.ProductVariantRepository;
import com.shopsmart.product.StockStatus;
import com.shopsmart.user.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persistent server-side cart. Every response re-prices the cart from the catalog. */
@Service
public class CartService {

    public static final int MAX_QUANTITY_PER_LINE = 10;
    static final int MAX_LINES = 50;

    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductImageRepository imageRepository;
    private final PricingService pricingService;
    private final CouponService couponService;
    private final StoreSettingsService settingsService;
    private final ActivityService activityService;

    public CartService(CartRepository cartRepository, UserRepository userRepository,
                       ProductVariantRepository variantRepository, InventoryRepository inventoryRepository,
                       ProductImageRepository imageRepository, PricingService pricingService,
                       CouponService couponService, StoreSettingsService settingsService,
                       ActivityService activityService) {
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.variantRepository = variantRepository;
        this.inventoryRepository = inventoryRepository;
        this.imageRepository = imageRepository;
        this.pricingService = pricingService;
        this.couponService = couponService;
        this.settingsService = settingsService;
        this.activityService = activityService;
    }

    @Transactional
    public CartResponse get(Long userId) {
        return view(loadOrCreate(userId), userId);
    }

    @Transactional
    public CartResponse addItem(Long userId, Long variantId, int quantity) {
        Cart cart = loadOrCreate(userId);
        ProductVariant variant = variantRepository.findWithProduct(variantId)
                .filter(v -> v.isActive() && ProductService.isStorefrontVisible(v.getProduct()))
                .orElseThrow(() -> ApiException.notFound("Product variant"));
        CartItem item = cart.findItemByVariantId(variantId).orElse(null);
        int newQuantity = (item == null ? 0 : item.getQuantity()) + quantity;
        if (newQuantity > MAX_QUANTITY_PER_LINE) {
            throw ApiException.businessRule("You can buy at most " + MAX_QUANTITY_PER_LINE + " units of an item");
        }
        requireAvailable(variant, newQuantity);
        if (item == null) {
            if (cart.getItems().size() >= MAX_LINES) {
                throw ApiException.businessRule("Your cart is full");
            }
            item = new CartItem();
            item.setCart(cart);
            item.setVariant(variant);
            cart.getItems().add(item);
        }
        item.setQuantity(newQuantity);
        cartRepository.flush();
        activityService.record(userId, ActivityType.ADD_TO_CART, variant.getProduct().getId(),
                variant.getProduct().getCategory().getId(), Map.of("variantId", variantId, "quantity", quantity));
        return view(cart, userId);
    }

    @Transactional
    public CartResponse updateQuantity(Long userId, Long itemId, int quantity) {
        Cart cart = loadOrCreate(userId);
        CartItem item = findItem(cart, itemId);
        if (quantity == 0) {
            return removeItem(userId, itemId);
        }
        requireAvailable(item.getVariant(), quantity);
        item.setQuantity(quantity);
        cartRepository.flush();
        return view(cart, userId);
    }

    @Transactional
    public CartResponse removeItem(Long userId, Long itemId) {
        Cart cart = loadOrCreate(userId);
        CartItem item = findItem(cart, itemId);
        cart.getItems().remove(item);
        cartRepository.flush();
        Product product = item.getVariant().getProduct();
        activityService.record(userId, ActivityType.REMOVE_FROM_CART, product.getId(), product.getCategory().getId(),
                Map.of("variantId", item.getVariant().getId()));
        return view(cart, userId);
    }

    @Transactional
    public CartResponse clear(Long userId) {
        Cart cart = loadOrCreate(userId);
        cart.getItems().clear();
        cart.setCouponCode(null);
        cartRepository.flush();
        return view(cart, userId);
    }

    @Transactional
    public CartResponse applyCoupon(Long userId, String code) {
        Cart cart = loadOrCreate(userId);
        if (cart.getItems().isEmpty()) {
            throw ApiException.businessRule("Add items to your cart before applying a coupon");
        }
        BigDecimal subtotal = subtotal(cart);
        CouponEvaluation evaluation = couponService.evaluate(code, userId, subtotal);
        if (!evaluation.valid()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.INVALID_COUPON, evaluation.message());
        }
        cart.setCouponCode(evaluation.code());
        return view(cart, userId);
    }

    @Transactional
    public CartResponse removeCoupon(Long userId) {
        Cart cart = loadOrCreate(userId);
        cart.setCouponCode(null);
        return view(cart, userId);
    }

    /** Removes purchased quantities after a successful order (lines are dropped when fully bought). */
    @Transactional
    public void removePurchased(Long userId, Map<Long, Integer> purchasedByVariant) {
        cartRepository.findWithItemsByUserId(userId).ifPresent(cart -> {
            cart.getItems().removeIf(item -> {
                Integer bought = purchasedByVariant.get(item.getVariant().getId());
                if (bought == null) {
                    return false;
                }
                if (item.getQuantity() > bought) {
                    item.setQuantity(item.getQuantity() - bought);
                    return false;
                }
                return true;
            });
            cart.setCouponCode(null);
        });
    }

    @Transactional
    public Cart loadOrCreate(Long userId) {
        return cartRepository.findWithItemsByUserId(userId).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setUser(userRepository.getReferenceById(userId));
            return cartRepository.save(cart);
        });
    }

    private CartResponse view(Cart cart, Long userId) {
        List<Long> variantIds = cart.getItems().stream().map(item -> item.getVariant().getId()).toList();
        Map<Long, Inventory> stock = new HashMap<>();
        Map<Long, String> images = new HashMap<>();
        if (!variantIds.isEmpty()) {
            inventoryRepository.findByVariantIds(variantIds).forEach(inv -> stock.put(inv.getVariant().getId(), inv));
            List<Long> productIds = cart.getItems().stream().map(i -> i.getVariant().getProduct().getId()).toList();
            for (ProductImage image : imageRepository.findByProductIds(productIds)) {
                images.putIfAbsent(image.getProduct().getId(),
                        image.getThumbnailUrl() != null ? image.getThumbnailUrl() : image.getUrl());
            }
        }

        List<CartItemResponse> items = new ArrayList<>();
        List<PricingLine> lines = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            ProductVariant variant = item.getVariant();
            Product product = variant.getProduct();
            Inventory inventory = stock.get(variant.getId());
            int available = inventory == null ? 0 : Math.max(0, inventory.available());
            BigDecimal unitMrp = Money.of(variant.listPrice());
            BigDecimal unitPrice = Money.applyDiscount(variant.listPrice(), product.getDiscountPercent());
            String issue = null;
            if (!variant.isActive() || !ProductService.isStorefrontVisible(product)) {
                issue = "UNAVAILABLE";
                issues.add(product.getName() + " is no longer available");
            } else if (available == 0) {
                issue = "OUT_OF_STOCK";
                issues.add(product.getName() + " is out of stock");
            } else if (item.getQuantity() > available) {
                issue = "INSUFFICIENT_STOCK";
                issues.add("Only " + available + " unit(s) of " + product.getName() + " are available");
            }
            if (issue == null) {
                lines.add(new PricingLine(variant.getId(), item.getQuantity(), unitMrp, unitPrice));
            }
            items.add(new CartItemResponse(item.getId(), variant.getId(), product.getId(), product.getName(),
                    product.getSlug(), product.getBrand().getName(), variant.getName(), variant.getSku(),
                    Map.copyOf(variant.getAttributes()), images.get(product.getId()), unitMrp, unitPrice,
                    item.getQuantity(), Money.of(unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()))),
                    available, Math.min(MAX_QUANTITY_PER_LINE, available),
                    StockStatus.of(available, inventory == null ? 0 : inventory.getLowStockThreshold()), issue));
        }

        BigDecimal subtotal = lines.stream().map(PricingLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        CartResponse.AppliedCoupon applied = null;
        BigDecimal couponDiscount = BigDecimal.ZERO;
        if (cart.getCouponCode() != null) {
            CouponEvaluation evaluation = couponService.evaluate(cart.getCouponCode(), userId, subtotal);
            applied = new CartResponse.AppliedCoupon(evaluation.code(), evaluation.valid(), evaluation.message());
            if (evaluation.valid()) {
                couponDiscount = evaluation.discount();
            } else {
                issues.add("Coupon " + evaluation.code() + ": " + evaluation.message());
            }
        }
        PriceQuote quote = pricingService.quote(lines, couponDiscount, settingsService.current());
        boolean canCheckout = !items.isEmpty() && issues.isEmpty();
        return new CartResponse(items, quote, applied, canCheckout, issues);
    }

    private BigDecimal subtotal(Cart cart) {
        return cart.getItems().stream()
                .filter(item -> item.getVariant().isActive())
                .map(item -> Money.applyDiscount(item.getVariant().listPrice(),
                        item.getVariant().getProduct().getDiscountPercent())
                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void requireAvailable(ProductVariant variant, int quantity) {
        int available = inventoryRepository.findByVariantId(variant.getId()).map(Inventory::available).orElse(0);
        if (available <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INSUFFICIENT_STOCK,
                    variant.getProduct().getName() + " is out of stock");
        }
        if (quantity > available) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INSUFFICIENT_STOCK,
                    "Only " + available + " unit(s) of " + variant.getProduct().getName() + " are available");
        }
    }

    private static CartItem findItem(Cart cart, Long itemId) {
        return cart.getItems().stream().filter(item -> item.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Cart item"));
    }
}

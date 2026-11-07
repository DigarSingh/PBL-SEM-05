package com.shopsmart.order;

import com.shopsmart.admin.StoreSettings;
import com.shopsmart.admin.StoreSettingsService;
import com.shopsmart.cart.Cart;
import com.shopsmart.cart.CartItem;
import com.shopsmart.cart.CartRepository;
import com.shopsmart.cart.CartService;
import com.shopsmart.common.exception.ApiException;
import com.shopsmart.common.exception.ErrorCode;
import com.shopsmart.common.util.Money;
import com.shopsmart.coupon.CouponEvaluation;
import com.shopsmart.coupon.CouponRules;
import com.shopsmart.coupon.CouponService;
import com.shopsmart.inventory.InsufficientStockException;
import com.shopsmart.inventory.Inventory;
import com.shopsmart.inventory.InventoryService;
import com.shopsmart.order.dto.CheckoutRequest;
import com.shopsmart.order.events.OrderPlacedEvent;
import com.shopsmart.payment.Payment;
import com.shopsmart.payment.PaymentMethod;
import com.shopsmart.payment.PaymentRepository;
import com.shopsmart.payment.PaymentStatus;
import com.shopsmart.pricing.PriceQuote;
import com.shopsmart.pricing.PricingLine;
import com.shopsmart.pricing.PricingService;
import com.shopsmart.product.Product;
import com.shopsmart.product.ProductImage;
import com.shopsmart.product.ProductImageRepository;
import com.shopsmart.product.ProductService;
import com.shopsmart.product.ProductVariant;
import com.shopsmart.user.Address;
import com.shopsmart.user.AddressService;
import com.shopsmart.user.User;
import com.shopsmart.user.UserRepository;
import com.shopsmart.user.UserStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Team Member 1 — Backend Core & Concurrency Lead
 * <p>
 * Atomic checkout transaction pipeline:
 * <ul>
 *   <li>Transaction management with {@code READ_COMMITTED} isolation</li>
 *   <li>Checkout idempotency and replay protection via {@code Idempotency-Key}</li>
 *   <li>Pessimistic cart locking preventing race conditions on duplicate checkouts</li>
 *   <li>Deadlock-free inventory reservation and consistent order processing</li>
 *   <li>Dynamic server-side re-pricing and coupon redemption</li>
 * </ul>
 * <p>
 * Concurrency: the cart row is locked first, so two checkouts of the same cart (double click,
 * two tabs) run one after the other; the transaction uses READ COMMITTED so the second one sees
 * the first one's committed effects (emptied cart, existing order, coupon usage) after the lock.
 */
@Service
public class CheckoutService {

    private final UserRepository userRepository;
    private final AddressService addressService;
    private final CartRepository cartRepository;
    private final CartService cartService;
    private final InventoryService inventoryService;
    private final PricingService pricingService;
    private final CouponService couponService;
    private final StoreSettingsService settingsService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductImageRepository imageRepository;
    private final OrderNumberGenerator orderNumbers;
    private final OrderLifecycleService lifecycle;
    private final ApplicationEventPublisher events;

    public CheckoutService(UserRepository userRepository, AddressService addressService,
                           CartRepository cartRepository, CartService cartService, InventoryService inventoryService,
                           PricingService pricingService, CouponService couponService,
                           StoreSettingsService settingsService, OrderRepository orderRepository,
                           PaymentRepository paymentRepository, ProductImageRepository imageRepository,
                           OrderNumberGenerator orderNumbers, OrderLifecycleService lifecycle,
                           ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.addressService = addressService;
        this.cartRepository = cartRepository;
        this.cartService = cartService;
        this.inventoryService = inventoryService;
        this.pricingService = pricingService;
        this.couponService = couponService;
        this.settingsService = settingsService;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.imageRepository = imageRepository;
        this.orderNumbers = orderNumbers;
        this.lifecycle = lifecycle;
        this.events = events;
    }

    /** {@code existing} is true when an already-created order is returned instead of a new one. */
    public record Result(Order order, boolean existing) {
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result createOrder(Long userId, CheckoutRequest request, String idempotencyKey) {
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("Your account cannot place orders. Please contact support.");
        }
        Optional<Cart> lockedCart = cartRepository.lockByUserId(userId);
        Optional<Order> replay = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (replay.isPresent()) {
            return new Result(replay.get(), true);
        }
        StoreSettings settings = settingsService.current();
        PaymentMethod method = request.paymentMethod();
        if (method == PaymentMethod.COD && !settings.isCodEnabled()) {
            throw ApiException.businessRule("Cash on Delivery is currently unavailable");
        }
        if (method == PaymentMethod.RAZORPAY && !settingsService.onlinePaymentsAvailable(settings)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.PAYMENT_UNAVAILABLE,
                    "Online payment is currently unavailable. Please choose Cash on Delivery.");
        }
        Address address = addressService.loadOwned(userId, request.addressId());
        if (lockedCart.isEmpty()) {
            throw ApiException.businessRule("Your cart is empty");
        }
        Cart cart = cartService.loadOrCreate(userId);
        if (cart.getItems().isEmpty()) {
            throw ApiException.businessRule("Your cart is empty");
        }
        if (method == PaymentMethod.RAZORPAY) {
            Optional<Order> reusable = reusablePendingOrder(userId, cart, address);
            if (reusable.isPresent()) {
                return new Result(reusable.get(), true);
            }
        }

        List<CartItem> cartItems = List.copyOf(cart.getItems());
        Map<Long, Inventory> locked = inventoryService.lockForVariants(
                cartItems.stream().map(item -> item.getVariant().getId()).toList());

        List<InsufficientStockException.Shortage> shortages = new ArrayList<>();
        List<PricingLine> lines = new ArrayList<>();
        for (CartItem item : cartItems) {
            ProductVariant variant = item.getVariant();
            Product product = variant.getProduct();
            Inventory inventory = locked.get(variant.getId());
            boolean sellable = variant.isActive() && ProductService.isStorefrontVisible(product);
            int available = sellable && inventory != null ? inventory.available() : 0;
            if (available < item.getQuantity()) {
                shortages.add(new InsufficientStockException.Shortage(variant.getId(), variant.getSku(),
                        product.getName(), item.getQuantity(), Math.max(0, available)));
                continue;
            }
            lines.add(new PricingLine(variant.getId(), item.getQuantity(), Money.of(variant.listPrice()),
                    Money.applyDiscount(variant.listPrice(), product.getDiscountPercent())));
        }
        if (!shortages.isEmpty()) {
            throw new InsufficientStockException(shortages);
        }

        BigDecimal subtotal = lines.stream().map(PricingLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        CouponEvaluation coupon = null;
        if (StringUtils.hasText(cart.getCouponCode())) {
            coupon = couponService.lockForCheckout(cart.getCouponCode(), userId, subtotal);
        }
        PriceQuote quote = pricingService.quote(lines, coupon == null ? BigDecimal.ZERO : coupon.discount(), settings);
        if (method == PaymentMethod.COD && settings.getCodMaxOrderAmount() != null
                && quote.total().compareTo(settings.getCodMaxOrderAmount()) > 0) {
            throw ApiException.businessRule("Cash on Delivery is available for orders up to "
                    + CouponRules.format(settings.getCodMaxOrderAmount()) + ". Please pay online.");
        }

        Order order = buildOrder(user, address, request, idempotencyKey, cartItems, quote, coupon, method);
        orderRepository.saveAndFlush(order);
        if (coupon != null) {
            couponService.recordRedemption(coupon, userId, order.getId());
        }
        for (OrderItem item : order.getItems()) {
            Inventory inventory = locked.get(item.getVariantId());
            if (method == PaymentMethod.COD) {
                inventoryService.sell(inventory, item.getQuantity(), order.getId());
            } else {
                inventoryService.reserve(inventory, item.getQuantity(), order.getId());
            }
        }

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setMethod(method);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(quote.total());
        payment.setCurrency(quote.currency());
        paymentRepository.save(payment);

        if (method == PaymentMethod.COD) {
            lifecycle.markPlaced(order);
            lifecycle.recordSale(order);
            cartService.removePurchased(userId, OrderLifecycleService.quantitiesByVariant(order));
            events.publishEvent(new OrderPlacedEvent(order.getId()));
        }
        return new Result(order, false);
    }

    /**
     * An unpaid online order with exactly the same items, coupon and address is re-used instead
     * of reserving the same stock twice (e.g. the shopper reloaded the page and pressed "Pay" again).
     */
    private Optional<Order> reusablePendingOrder(Long userId, Cart cart, Address address) {
        Map<Long, Integer> contents = cart.getItems().stream().collect(Collectors.toMap(
                item -> item.getVariant().getId(), CartItem::getQuantity, Integer::sum));
        return orderRepository.findPendingPayment(userId).stream()
                .filter(pending -> OrderLifecycleService.quantitiesByVariant(pending).equals(contents))
                .filter(pending -> Objects.equals(pending.getCouponCode(), CouponService.normalize(cart.getCouponCode())))
                .filter(pending -> pending.getShippingAddress().getLine1().equals(address.getLine1())
                        && pending.getShippingAddress().getPostalCode().equals(address.getPostalCode()))
                .findFirst();
    }

    private Order buildOrder(User user, Address address, CheckoutRequest request, String idempotencyKey,
                             List<CartItem> cartItems, PriceQuote quote, CouponEvaluation coupon,
                             PaymentMethod method) {
        Order order = new Order();
        order.setOrderNumber(orderNumbers.next());
        order.setUser(user);
        order.setPaymentMethod(method);
        order.setStatus(method == PaymentMethod.COD ? OrderStatus.PLACED : OrderStatus.PENDING_PAYMENT);
        order.setMrpTotal(quote.mrpTotal());
        order.setSubtotal(quote.subtotal());
        order.setCouponCode(coupon == null ? null : coupon.code());
        order.setCouponDiscount(quote.couponDiscount());
        order.setShippingFee(quote.shippingFee());
        order.setTaxRate(quote.taxRate());
        order.setTaxInclusive(quote.taxInclusive());
        order.setTaxAmount(quote.taxAmount());
        order.setTotalAmount(quote.total());
        order.setCurrency(quote.currency());
        order.setShippingAddress(new ShippingAddress(address.getFullName(), address.getPhone(), address.getLine1(),
                address.getLine2(), address.getLandmark(), address.getCity(), address.getState(),
                address.getPostalCode(), address.getCountry()));
        order.setCustomerNote(StringUtils.hasText(request.customerNote()) ? request.customerNote().trim() : null);
        order.setIdempotencyKey(idempotencyKey);

        Map<Long, String> images = new HashMap<>();
        for (ProductImage image : imageRepository.findByProductIds(
                cartItems.stream().map(item -> item.getVariant().getProduct().getId()).toList())) {
            images.putIfAbsent(image.getProduct().getId(),
                    image.getThumbnailUrl() != null ? image.getThumbnailUrl() : image.getUrl());
        }
        for (CartItem cartItem : cartItems) {
            ProductVariant variant = cartItem.getVariant();
            Product product = variant.getProduct();
            BigDecimal unitPrice = Money.applyDiscount(variant.listPrice(), product.getDiscountPercent());
            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            item.setVariantId(variant.getId());
            item.setProductName(product.getName());
            item.setVariantName(variant.getName());
            item.setSku(variant.getSku());
            item.setImageUrl(images.get(product.getId()));
            item.setUnitMrp(Money.of(variant.listPrice()));
            item.setUnitPrice(unitPrice);
            item.setQuantity(cartItem.getQuantity());
            item.setLineTotal(Money.of(unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()))));
            order.addItem(item);
        }
        OrderStatusHistory created = new OrderStatusHistory();
        created.setToStatus(order.getStatus());
        created.setChangedByUserId(user.getId());
        created.setNote(method == PaymentMethod.COD ? "Order placed (Cash on Delivery)" : "Awaiting online payment");
        order.addHistory(created);
        return order;
    }
}

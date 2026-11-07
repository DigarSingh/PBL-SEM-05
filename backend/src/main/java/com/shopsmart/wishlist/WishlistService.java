package com.shopsmart.wishlist;

import com.shopsmart.activity.ActivityService;
import com.shopsmart.activity.ActivityType;
import com.shopsmart.cart.CartService;
import com.shopsmart.common.exception.ApiException;
import com.shopsmart.product.Product;
import com.shopsmart.product.ProductCardAssembler;
import com.shopsmart.product.ProductRepository;
import com.shopsmart.product.ProductService;
import com.shopsmart.product.dto.ProductSummaryResponse;
import com.shopsmart.user.UserRepository;
import com.shopsmart.wishlist.dto.WishlistResponse;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WishlistService {

    static final int MAX_ITEMS = 100;

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductCardAssembler cardAssembler;
    private final CartService cartService;
    private final ActivityService activityService;

    public WishlistService(WishlistRepository wishlistRepository, ProductRepository productRepository,
                           UserRepository userRepository, ProductCardAssembler cardAssembler,
                           CartService cartService, ActivityService activityService) {
        this.wishlistRepository = wishlistRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.cardAssembler = cardAssembler;
        this.cartService = cartService;
        this.activityService = activityService;
    }

    @Transactional
    public WishlistResponse get(Long userId) {
        return view(loadOrCreate(userId));
    }

    @Transactional(readOnly = true)
    public List<Long> productIds(Long userId) {
        return wishlistRepository.findWithItemsByUserId(userId)
                .map(w -> w.getItems().stream().map(item -> item.getProduct().getId()).toList())
                .orElse(List.of());
    }

    /** Idempotent: adding a product that is already saved is a no-op. */
    @Transactional
    public WishlistResponse add(Long userId, Long productId) {
        Wishlist wishlist = loadOrCreate(userId);
        boolean present = wishlist.getItems().stream().anyMatch(item -> item.getProduct().getId().equals(productId));
        if (!present) {
            Product product = productRepository.findWithBrandAndCategory(productId)
                    .filter(ProductService::isStorefrontVisible)
                    .orElseThrow(() -> ApiException.notFound("Product"));
            if (wishlist.getItems().size() >= MAX_ITEMS) {
                throw ApiException.businessRule("Your wishlist is full");
            }
            WishlistItem item = new WishlistItem();
            item.setWishlist(wishlist);
            item.setProduct(product);
            wishlist.getItems().addFirst(item);
            wishlistRepository.flush();
            activityService.record(userId, ActivityType.WISHLIST_ADD, productId, product.getCategory().getId(), null);
        }
        return view(wishlist);
    }

    @Transactional
    public WishlistResponse remove(Long userId, Long productId) {
        Wishlist wishlist = loadOrCreate(userId);
        wishlist.getItems().stream().filter(item -> item.getProduct().getId().equals(productId)).findFirst()
                .ifPresent(item -> {
                    wishlist.getItems().remove(item);
                    activityService.record(userId, ActivityType.WISHLIST_REMOVE, productId,
                            item.getProduct().getCategory().getId(), null);
                });
        wishlistRepository.flush();
        return view(wishlist);
    }

    /** Adds the product's default in-stock variant to the cart and removes it from the wishlist. */
    @Transactional
    public WishlistResponse moveToCart(Long userId, Long productId) {
        Product product = productRepository.findWithBrandAndCategory(productId)
                .filter(ProductService::isStorefrontVisible)
                .orElseThrow(() -> ApiException.notFound("Product"));
        ProductSummaryResponse card = cardAssembler.toCards(List.of(product)).getFirst();
        if (!card.inStock() || card.defaultVariantId() == null) {
            throw ApiException.businessRule(product.getName() + " is out of stock");
        }
        cartService.addItem(userId, card.defaultVariantId(), 1);
        return remove(userId, productId);
    }

    private Wishlist loadOrCreate(Long userId) {
        return wishlistRepository.findWithItemsByUserId(userId).orElseGet(() -> {
            Wishlist wishlist = new Wishlist();
            wishlist.setUser(userRepository.getReferenceById(userId));
            return wishlistRepository.save(wishlist);
        });
    }

    private WishlistResponse view(Wishlist wishlist) {
        List<Product> products = wishlist.getItems().stream().map(WishlistItem::getProduct).toList();
        Map<Long, ProductSummaryResponse> cards = cardAssembler.toCardMap(products);
        List<WishlistResponse.Item> items = wishlist.getItems().stream()
                .filter(item -> cards.containsKey(item.getProduct().getId()))
                .map(item -> new WishlistResponse.Item(cards.get(item.getProduct().getId()), item.getCreatedAt()))
                .toList();
        return new WishlistResponse(items, items.size());
    }
}

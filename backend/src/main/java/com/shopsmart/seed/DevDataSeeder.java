package com.shopsmart.seed;

import com.shopsmart.brand.Brand;
import com.shopsmart.brand.BrandRepository;
import com.shopsmart.category.Category;
import com.shopsmart.category.CategoryRepository;
import com.shopsmart.common.util.Money;
import com.shopsmart.common.util.Slugs;
import com.shopsmart.config.AppProperties;
import com.shopsmart.coupon.Coupon;
import com.shopsmart.coupon.CouponRepository;
import com.shopsmart.coupon.DiscountType;
import com.shopsmart.inventory.InventoryService;
import com.shopsmart.product.Product;
import com.shopsmart.product.ProductDerivedFields;
import com.shopsmart.product.ProductImage;
import com.shopsmart.product.ProductRepository;
import com.shopsmart.product.ProductSpecification;
import com.shopsmart.product.ProductStatus;
import com.shopsmart.product.ProductVariant;
import com.shopsmart.storage.StorageService;
import com.shopsmart.user.Address;
import com.shopsmart.user.AddressRepository;
import com.shopsmart.user.Role;
import com.shopsmart.user.RoleRepository;
import com.shopsmart.user.User;
import com.shopsmart.user.UserRepository;
import com.shopsmart.user.UserStatus;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * Development seed data: catalog from {@code seed/catalog.json}, demo accounts, coupons and 120
 * days of realistic history. Never active in the prod profile; runs only when enabled and the
 * catalog is empty, so restarts never duplicate data.
 */
@Component
@Profile("!prod & !test")
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    static final String DEMO_CUSTOMER_EMAIL = "customer@shopsmart.test";
    private static final String UNSPLASH = "https://images.unsplash.com/photo-%s?auto=format&fit=crop&w=%d&q=%d";

    private static final String[][] CUSTOMERS = {
            {"Ishita Rao", DEMO_CUSTOMER_EMAIL, "+91 98450 12345", "Bengaluru", "Karnataka", "560034", "12, 5th Cross, Koramangala 4th Block"},
            {"Rohan Shah", "rohan.shah@shopsmart.test", "+91 98200 45678", "Mumbai", "Maharashtra", "400050", "704, Sea Breeze Apartments, Bandra West"},
            {"Maya Nair", "maya.nair@shopsmart.test", "+91 94470 23456", "Kochi", "Kerala", "682020", "Villa 9, Palm Grove, Kadavanthra"},
            {"Dev Kapoor", "dev.kapoor@shopsmart.test", "+91 98110 34567", "New Delhi", "Delhi", "110017", "B-24, Malviya Nagar"},
            {"Ananya Singh", "ananya.singh@shopsmart.test", "+91 97650 56789", "Pune", "Maharashtra", "411045", "Flat 302, Baner Heights, Baner"},
            {"Arjun Mehta", "arjun.mehta@shopsmart.test", "+91 99090 67890", "Ahmedabad", "Gujarat", "380015", "15, Satellite Road, Jodhpur"},
            {"Priya Iyer", "priya.iyer@shopsmart.test", "+91 98840 78901", "Chennai", "Tamil Nadu", "600040", "22, 3rd Avenue, Anna Nagar"},
            {"Kabir Malhotra", "kabir.malhotra@shopsmart.test", "+91 98721 89012", "Chandigarh", "Chandigarh", "160022", "House 118, Sector 22-B"},
            {"Sneha Kulkarni", "sneha.kulkarni@shopsmart.test", "+91 98225 90123", "Nagpur", "Maharashtra", "440010", "Plot 7, Dharampeth"},
            {"Vikram Reddy", "vikram.reddy@shopsmart.test", "+91 98490 01234", "Hyderabad", "Telangana", "500081", "Flat 1102, Cyber Towers Road, Madhapur"},
            {"Nisha Verma", "nisha.verma@shopsmart.test", "+91 99530 11223", "Jaipur", "Rajasthan", "302017", "C-41, Malviya Nagar"},
            {"Aditya Joshi", "aditya.joshi@shopsmart.test", "+91 90040 22334", "Thane", "Maharashtra", "400601", "A-9, Hiranandani Estate"},
            {"Meera Pillai", "meera.pillai@shopsmart.test", "+91 94960 33445", "Thiruvananthapuram", "Kerala", "695010", "TC 25/1180, Vazhuthacaud"},
            {"Rahul Bose", "rahul.bose@shopsmart.test", "+91 98300 44556", "Kolkata", "West Bengal", "700019", "8/1, Ballygunge Place"},
            {"Tanvi Desai", "tanvi.desai@shopsmart.test", "+91 98980 55667", "Surat", "Gujarat", "395007", "402, Green Park, Vesu"}
    };

    private final AppProperties properties;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate tx;
    private final JdbcTemplate jdbc;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storage;
    private final SeedHistoryGenerator historyGenerator;
    private final Clock clock;

    public DevDataSeeder(AppProperties properties, JsonMapper jsonMapper, TransactionTemplate tx, JdbcTemplate jdbc,
                         CategoryRepository categoryRepository, BrandRepository brandRepository,
                         ProductRepository productRepository, InventoryService inventoryService,
                         CouponRepository couponRepository, UserRepository userRepository,
                         RoleRepository roleRepository, AddressRepository addressRepository,
                         PasswordEncoder passwordEncoder, StorageService storage,
                         SeedHistoryGenerator historyGenerator, Clock clock) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.tx = tx;
        this.jdbc = jdbc;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.couponRepository = couponRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.addressRepository = addressRepository;
        this.passwordEncoder = passwordEncoder;
        this.storage = storage;
        this.historyGenerator = historyGenerator;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (productRepository.count() > 0) {
            log.info("Seed data skipped: the catalog already contains products");
            return;
        }
        long started = System.currentTimeMillis();
        SeedCatalog catalog = readCatalog();
        Map<String, Product> products = tx.execute(status -> seedCatalog(catalog));
        tx.executeWithoutResult(status -> seedCoupons(catalog.coupons()));
        List<User> customers = tx.execute(status -> seedUsers());
        tx.executeWithoutResult(status -> backdateCatalog(catalog, products));
        SeedHistoryGenerator.Summary summary = historyGenerator.generate(catalog, products, customers);
        log.info("Seeded {} categories, {} brands, {} products, {} customers, {} orders, {} reviews in {} ms",
                catalog.categories().size(), catalog.brands().size(), products.size(), customers.size(),
                summary.orders(), summary.reviews(), System.currentTimeMillis() - started);
        if (StringUtils.hasText(properties.seed().adminPassword())) {
            log.info("Development admin account: {} (password from SEED_ADMIN_PASSWORD)", properties.seed().adminEmail());
        }
        if (StringUtils.hasText(properties.seed().customerPassword())) {
            log.info("Development customer account: {} (password from SEED_CUSTOMER_PASSWORD)", DEMO_CUSTOMER_EMAIL);
        }
    }

    private SeedCatalog readCatalog() {
        try (InputStream input = new ClassPathResource("seed/catalog.json").getInputStream()) {
            return jsonMapper.readValue(input, SeedCatalog.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read seed catalog", e);
        }
    }

    private Map<String, Product> seedCatalog(SeedCatalog catalog) {
        Map<String, Category> categories = new HashMap<>();
        int position = 0;
        for (SeedCatalog.Category source : catalog.categories()) {
            Category category = new Category();
            category.setName(source.name());
            category.setSlug(source.slug());
            category.setDescription(source.description());
            category.setImageUrl(imageUrl(source.image(), 800, 75));
            category.setSortOrder(position++);
            categories.put(source.slug(), categoryRepository.save(category));
        }
        Map<String, Brand> brands = new HashMap<>();
        for (SeedCatalog.Brand source : catalog.brands()) {
            Brand brand = new Brand();
            brand.setName(source.name());
            brand.setSlug(Slugs.slugify(source.name()));
            brand.setDescription(source.description());
            brands.put(source.name(), brandRepository.save(brand));
        }
        Map<String, Product> products = new HashMap<>();
        for (SeedCatalog.Product source : catalog.products()) {
            Product product = new Product();
            product.setName(source.name());
            product.setSlug(Slugs.unique(Slugs.slugify(source.name()), productRepository::existsBySlug));
            product.setBrand(brands.get(source.brand()));
            product.setCategory(categories.get(source.category()));
            product.setShortDescription(source.shortDescription());
            product.setDescription(source.description());
            product.setPrice(Money.of(source.price()));
            product.setDiscountPercent(source.discount());
            product.setStatus(ProductStatus.ACTIVE);
            product.setFeatured(Boolean.TRUE.equals(source.featured()));
            source.tags().forEach(tag -> product.getTags().add(tag.toLowerCase(Locale.ROOT)));
            source.specs().forEach(spec -> product.getSpecifications().add(new ProductSpecification(spec.get(0), spec.get(1))));
            int imageOrder = 0;
            for (String reference : source.images()) {
                ProductImage image = new ProductImage();
                image.setUrl(imageUrl(reference, 1200, 80));
                image.setThumbnailUrl(imageUrl(reference, 480, 70));
                image.setAltText(source.name());
                image.setSortOrder(imageOrder++);
                product.addImage(image);
            }
            int variantOrder = 0;
            for (SeedCatalog.Variant sourceVariant : source.variants()) {
                ProductVariant variant = new ProductVariant();
                variant.setSku(sourceVariant.sku());
                variant.setName(sourceVariant.name());
                variant.setPriceOverride(sourceVariant.price() == null ? null : Money.of(sourceVariant.price()));
                variant.setSortOrder(variantOrder++);
                if (sourceVariant.attributes() != null) {
                    variant.getAttributes().putAll(sourceVariant.attributes());
                }
                product.addVariant(variant);
            }
            ProductDerivedFields.refresh(product);
            productRepository.save(product);
            for (int i = 0; i < source.variants().size(); i++) {
                SeedCatalog.Variant sourceVariant = source.variants().get(i);
                inventoryService.createForVariant(product.getVariants().get(i), sourceVariant.stock(),
                        sourceVariant.threshold() == null ? 5 : sourceVariant.threshold());
            }
            products.put(source.name(), product);
        }
        return products;
    }

    private void seedCoupons(List<SeedCatalog.Coupon> coupons) {
        Instant now = clock.instant();
        for (SeedCatalog.Coupon source : coupons) {
            Coupon coupon = new Coupon();
            coupon.setCode(source.code());
            coupon.setDescription(source.description());
            coupon.setDiscountType(DiscountType.valueOf(source.type()));
            coupon.setDiscountValue(Money.of(source.value()));
            coupon.setMinOrderAmount(source.minOrder() == null ? null : Money.of(source.minOrder()));
            coupon.setMaxDiscountAmount(source.maxDiscount() == null ? null : Money.of(source.maxDiscount()));
            coupon.setUsageLimit(source.usageLimit());
            coupon.setPerUserLimit(source.perUserLimit());
            if (source.expiresInDays() != null) {
                coupon.setStartsAt(now.minus(Duration.ofDays(120)));
                coupon.setExpiresAt(now.plus(Duration.ofDays(source.expiresInDays())));
            }
            coupon.setActive(source.active() == null || source.active());
            couponRepository.save(coupon);
        }
    }

    /** Demo accounts. Passwords come from SEED_* environment variables; none are hard-coded. */
    private List<User> seedUsers() {
        Role customerRole = roleRepository.findByName(Role.CUSTOMER).orElseThrow();
        Role adminRole = roleRepository.findByName(Role.ADMIN).orElseThrow();
        Instant now = clock.instant();
        String adminPassword = properties.seed().adminPassword();
        if (StringUtils.hasText(adminPassword) && !userRepository.existsByEmail(properties.seed().adminEmail())) {
            User admin = new User();
            admin.setEmail(properties.seed().adminEmail().toLowerCase(Locale.ROOT));
            admin.setFullName("Aarav Menon");
            admin.setPhone("+91 98860 00001");
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setStatus(UserStatus.ACTIVE);
            admin.setEmailVerifiedAt(now);
            admin.getRoles().add(adminRole);
            userRepository.save(admin);
        } else if (!StringUtils.hasText(adminPassword)) {
            log.warn("SEED_ADMIN_PASSWORD is not set: no development admin account was created");
        }

        String customerPassword = properties.seed().customerPassword();
        // Without a configured password the demo customers exist (for history) but cannot sign in.
        String hash = passwordEncoder.encode(StringUtils.hasText(customerPassword) ? customerPassword
                : java.util.UUID.randomUUID() + "Aa1");
        List<User> customers = new ArrayList<>();
        Random random = new Random(7);
        for (String[] row : CUSTOMERS) {
            User customer = new User();
            customer.setFullName(row[0]);
            customer.setEmail(row[1]);
            customer.setPhone(row[2]);
            customer.setPasswordHash(hash);
            customer.setStatus(UserStatus.ACTIVE);
            customer.setEmailVerifiedAt(now);
            customer.getRoles().add(customerRole);
            userRepository.save(customer);

            Address address = new Address();
            address.setUser(customer);
            address.setLabel("Home");
            address.setFullName(row[0]);
            address.setPhone(row[2]);
            address.setLine1(row[6]);
            address.setCity(row[3]);
            address.setState(row[4]);
            address.setPostalCode(row[5]);
            address.setCountry("IN");
            address.setDefaultAddress(true);
            addressRepository.save(address);
            if (random.nextInt(3) == 0) {
                Address work = new Address();
                work.setUser(customer);
                work.setLabel("Work");
                work.setFullName(row[0]);
                work.setPhone(row[2]);
                work.setLine1("Floor " + (2 + random.nextInt(12)) + ", Tech Park Tower " + (char) ('A' + random.nextInt(4)));
                work.setLandmark("Near metro station");
                work.setCity(row[3]);
                work.setState(row[4]);
                work.setPostalCode(row[5]);
                work.setCountry("IN");
                addressRepository.save(work);
            }
            customers.add(customer);
        }
        return customers;
    }

    /** Spreads catalog and account creation dates so "newest" sorting and customer analytics look real. */
    private void backdateCatalog(SeedCatalog catalog, Map<String, Product> products) {
        Random random = new Random(11);
        Instant now = clock.instant();
        for (SeedCatalog.Product source : catalog.products()) {
            Product product = products.get(source.name());
            int ageDays = "rising".equals(source.trend()) && random.nextBoolean() ? 20 + random.nextInt(60)
                    : 130 + random.nextInt(260);
            jdbc.update("update products set created_at = ? where id = ?",
                    SeedHistoryGenerator.utc(now.minus(Duration.ofDays(ageDays))), product.getId());
        }
        List<Long> customerIds = jdbc.queryForList("select u.id from users u join user_roles ur on ur.user_id = u.id "
                + "join roles r on r.id = ur.role_id where r.name = 'CUSTOMER' order by u.id", Long.class);
        for (Long id : customerIds) {
            jdbc.update("update users set created_at = ? where id = ?",
                    SeedHistoryGenerator.utc(now.minus(Duration.ofDays(125 + random.nextInt(90)))), id);
        }
    }

    private String imageUrl(String reference, int width, int quality) {
        if (reference.startsWith("u:")) {
            return UNSPLASH.formatted(reference.substring(2), width, quality);
        }
        if (reference.startsWith("svg:")) {
            String name = reference.substring(4);
            String key = "seed/" + name + ".svg";
            try (InputStream input = new ClassPathResource("seed/images/" + name + ".svg").getInputStream()) {
                return storage.store(key, input.readAllBytes(), "image/svg+xml");
            } catch (IOException e) {
                throw new UncheckedIOException("Missing seed illustration " + name, e);
            }
        }
        return reference;
    }

    static BigDecimal price(BigDecimal value) {
        return Money.of(value);
    }
}

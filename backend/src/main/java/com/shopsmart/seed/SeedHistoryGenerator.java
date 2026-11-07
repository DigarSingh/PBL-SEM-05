package com.shopsmart.seed;

import com.shopsmart.admin.StoreSettings;
import com.shopsmart.admin.StoreSettingsService;
import com.shopsmart.common.util.BusinessTime;
import com.shopsmart.common.util.Money;
import com.shopsmart.pricing.PriceQuote;
import com.shopsmart.pricing.PricingLine;
import com.shopsmart.pricing.PricingService;
import com.shopsmart.product.Product;
import com.shopsmart.product.ProductVariant;
import com.shopsmart.user.User;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Generates 120 days of plausible store history with a fixed random seed: orders whose volume
 * follows each product's demand profile (rising / declining / stable / recent spike), weekly
 * seasonality, fulfilment timelines, payments, reviews, product views, searches, wishlists and
 * behavioural events. Rows are inserted with JDBC so historical timestamps are kept.
 */
@Component
@Profile("!prod & !test")
public class SeedHistoryGenerator {

    static final int DAYS = 120;
    private static final double VOLUME_SCALE = 0.13;
    private static final char[] ORDER_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyMMdd").withZone(BusinessTime.ZONE);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final PricingService pricingService;
    private final StoreSettingsService settingsService;
    private final Clock clock;

    public SeedHistoryGenerator(JdbcTemplate jdbc, TransactionTemplate tx, PricingService pricingService,
                                StoreSettingsService settingsService, Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.pricingService = pricingService;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    public record Summary(int orders, int reviews) {
    }

    private record Line(SeedCatalog.Product source, Product product, ProductVariant variant, int quantity) {
    }

    public Summary generate(SeedCatalog catalog, Map<String, Product> products, List<User> customers) {
        return tx.execute(status -> doGenerate(catalog, products, customers));
    }

    private Summary doGenerate(SeedCatalog catalog, Map<String, Product> products, List<User> customers) {
        Random random = new Random(20260923L);
        Instant now = clock.instant();
        StoreSettings settings = settingsService.current();
        Long adminId = jdbc.query("select u.id from users u join user_roles ur on ur.user_id = u.id "
                + "join roles r on r.id = ur.role_id where r.name = 'ADMIN' order by u.id limit 1",
                rs -> rs.next() ? rs.getLong(1) : null);
        Map<Long, List<Long>> addressesByUser = new HashMap<>();
        jdbc.query("select id, user_id from addresses order by is_default desc, id", rs -> {
            addressesByUser.computeIfAbsent(rs.getLong(2), key -> new ArrayList<>()).add(rs.getLong(1));
        });
        double[] customerWeights = new double[customers.size()];
        for (int i = 0; i < customers.size(); i++) {
            customerWeights[i] = i == 0 ? 3.0 : 0.6 + random.nextDouble() * 1.8;
        }
        Long festiveCoupon = jdbc.query("select id from coupons where code = 'FESTIVE500'",
                rs -> rs.next() ? rs.getLong(1) : null);
        Map<Long, Integer> festiveUses = new HashMap<>();
        Set<String> orderNumbers = new HashSet<>();
        Map<Long, List<long[]>> deliveredPurchases = new HashMap<>();
        int orderCount = 0;

        LocalDate today = LocalDate.now(clock.withZone(BusinessTime.ZONE));
        for (int daysAgo = DAYS; daysAgo >= 0; daysAgo--) {
            LocalDate day = today.minusDays(daysAgo);
            double t = 1.0 - daysAgo / (double) DAYS;
            double weekday = switch (day.getDayOfWeek()) {
                case SATURDAY, SUNDAY -> 1.3;
                case FRIDAY -> 1.1;
                default -> 1.0;
            };
            List<SeedCatalog.Product> sources = catalog.products();
            double[] weights = new double[sources.size()];
            double total = 0;
            for (int i = 0; i < sources.size(); i++) {
                weights[i] = sources.get(i).demand() * trend(sources.get(i).trend(), t, daysAgo) * weekday * VOLUME_SCALE;
                total += weights[i];
            }
            int purchases = poisson(random, total);
            List<Line> pending = new ArrayList<>();
            for (int p = 0; p < purchases; p++) {
                SeedCatalog.Product source = sources.get(pick(random, weights, total));
                Product product = products.get(source.name());
                List<ProductVariant> variants = product.getVariants();
                ProductVariant variant = variants.get(random.nextInt(variants.size()));
                double roll = random.nextDouble();
                pending.add(new Line(source, product, variant, roll < 0.86 ? 1 : roll < 0.98 ? 2 : 3));
            }
            while (!pending.isEmpty()) {
                List<Line> orderLines = new ArrayList<>();
                orderLines.add(pending.removeFirst());
                if (!pending.isEmpty() && random.nextDouble() < 0.25) {
                    Line extra = pending.removeFirst();
                    if (orderLines.stream().noneMatch(line -> line.variant().getId().equals(extra.variant().getId()))) {
                        orderLines.add(extra);
                    } else {
                        pending.add(extra);
                    }
                }
                int customerIndex = pick(random, customerWeights, sum(customerWeights));
                User customer = customers.get(customerIndex);
                Instant placedAt = day.atStartOfDay(BusinessTime.ZONE).toInstant()
                        .plus(Duration.ofMinutes(8 * 60 + random.nextInt(15 * 60 + 30)));
                if (placedAt.isAfter(now.minus(Duration.ofMinutes(30)))) {
                    continue;
                }
                long orderId = insertOrder(random, customer, addressesByUser.get(customer.getId()).getFirst(),
                        orderLines, placedAt, now, settings, adminId, festiveCoupon, festiveUses, orderNumbers,
                        deliveredPurchases);
                if (orderId > 0) {
                    orderCount++;
                }
            }
        }
        int reviews = insertReviews(random, deliveredPurchases, catalog, products, now);
        insertBehaviour(random, catalog, products, customers, now);
        refreshAggregates();
        return new Summary(orderCount, reviews);
    }

    private long insertOrder(Random random, User customer, Long addressId, List<Line> lines, Instant placedAt,
                             Instant now, StoreSettings settings, Long adminId, Long festiveCoupon,
                             Map<Long, Integer> festiveUses, Set<String> orderNumbers,
                             Map<Long, List<long[]>> deliveredPurchases) {
        Map<String, Object> address = jdbc.queryForMap("select * from addresses where id = ?", addressId);
        List<PricingLine> pricing = new ArrayList<>();
        for (Line line : lines) {
            BigDecimal list = line.variant().listPrice();
            pricing.add(new PricingLine(line.variant().getId(), line.quantity(), Money.of(list),
                    Money.applyDiscount(list, line.product().getDiscountPercent())));
        }
        BigDecimal subtotal = pricing.stream().map(PricingLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean useCoupon = festiveCoupon != null && subtotal.compareTo(BigDecimal.valueOf(4999)) >= 0
                && festiveUses.getOrDefault(customer.getId(), 0) < 2 && random.nextDouble() < 0.18;
        PriceQuote quote = pricingService.quote(pricing, useCoupon ? BigDecimal.valueOf(500) : BigDecimal.ZERO, settings);

        long ageHours = Duration.between(placedAt, now).toHours();
        boolean online = random.nextDouble() < 0.62;
        String status = status(random, ageHours);
        String paymentMethod = online ? "RAZORPAY" : "COD";
        String orderNumber;
        do {
            StringBuilder builder = new StringBuilder("SS").append(ORDER_DATE.format(placedAt)).append('-');
            for (int i = 0; i < 6; i++) {
                builder.append(ORDER_ALPHABET[random.nextInt(ORDER_ALPHABET.length)]);
            }
            orderNumber = builder.toString();
        } while (!orderNumbers.add(orderNumber));

        List<Object[]> history = new ArrayList<>();
        Instant cursor = placedAt;
        Instant createdAt = online ? placedAt.minus(Duration.ofMinutes(2 + random.nextInt(6))) : placedAt;
        if (online) {
            history.add(new Object[] {null, "PENDING_PAYMENT", customer.getId(), "Awaiting online payment", createdAt});
            history.add(new Object[] {"PENDING_PAYMENT", "PLACED", null, "Payment received", placedAt});
        } else {
            history.add(new Object[] {null, "PLACED", customer.getId(), "Order placed (Cash on Delivery)", placedAt});
        }
        String[] pipeline = {"PLACED", "CONFIRMED", "PROCESSING", "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED"};
        long[] gapsMinutes = {60 + random.nextInt(300), 240 + random.nextInt(900), 900 + random.nextInt(1800),
                1200 + random.nextInt(2400), 120 + random.nextInt(360)};
        Instant deliveredAt = null;
        Instant cancelledAt = null;
        String cancelReason = null;
        String previous = "PLACED";
        if ("CANCELLED".equals(status)) {
            int stepsBefore = random.nextInt(3);
            for (int i = 1; i <= stepsBefore; i++) {
                cursor = cursor.plus(Duration.ofMinutes(gapsMinutes[i - 1]));
                history.add(new Object[] {previous, pipeline[i], adminId, null, cursor});
                previous = pipeline[i];
            }
            cancelledAt = cursor.plus(Duration.ofMinutes(30 + random.nextInt(600)));
            if (cancelledAt.isAfter(now)) {
                cancelledAt = now.minus(Duration.ofMinutes(5));
            }
            boolean byCustomer = !"PROCESSING".equals(previous) && random.nextBoolean();
            cancelReason = byCustomer ? pickOne(random, "Ordered by mistake", "Found a better price",
                    "Delivery date too late") : pickOne(random, "Item failed quality check", "Address not serviceable");
            history.add(new Object[] {previous, "CANCELLED", byCustomer ? customer.getId() : adminId, cancelReason,
                    cancelledAt});
        } else {
            int target = List.of(pipeline).indexOf(status);
            for (int i = 1; i <= target; i++) {
                Instant next = cursor.plus(Duration.ofMinutes(gapsMinutes[i - 1]));
                if (next.isAfter(now)) {
                    next = now.minus(Duration.ofMinutes(10L * (target - i + 1)));
                }
                cursor = next;
                history.add(new Object[] {previous, pipeline[i], adminId, null, cursor});
                previous = pipeline[i];
            }
            if ("DELIVERED".equals(status)) {
                deliveredAt = cursor;
            }
        }
        Instant updatedAt = (Instant) history.getLast()[4];

        KeyHolder keys = new GeneratedKeyHolder();
        String finalOrderNumber = orderNumber;
        Instant finalDelivered = deliveredAt;
        Instant finalCancelled = cancelledAt;
        String finalReason = cancelReason;
        LocalDate estimated = placedAt.atZone(BusinessTime.ZONE).toLocalDate().plusDays(4);
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into orders (order_number, user_id, status, payment_method, mrp_total, subtotal, coupon_code,
                      coupon_discount, shipping_fee, tax_rate, tax_inclusive, tax_amount, total_amount, currency,
                      ship_full_name, ship_phone, ship_line1, ship_line2, ship_landmark, ship_city, ship_state,
                      ship_postal_code, ship_country, estimated_delivery_date, placed_at, delivered_at, cancelled_at,
                      cancellation_reason, version, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'INR', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setString(i++, finalOrderNumber);
            ps.setLong(i++, customer.getId());
            ps.setString(i++, status);
            ps.setString(i++, paymentMethod);
            ps.setBigDecimal(i++, quote.mrpTotal());
            ps.setBigDecimal(i++, quote.subtotal());
            ps.setString(i++, useCoupon ? "FESTIVE500" : null);
            ps.setBigDecimal(i++, quote.couponDiscount());
            ps.setBigDecimal(i++, quote.shippingFee());
            ps.setBigDecimal(i++, quote.taxRate());
            ps.setBoolean(i++, quote.taxInclusive());
            ps.setBigDecimal(i++, quote.taxAmount());
            ps.setBigDecimal(i++, quote.total());
            ps.setString(i++, (String) address.get("full_name"));
            ps.setString(i++, (String) address.get("phone"));
            ps.setString(i++, (String) address.get("line1"));
            ps.setString(i++, (String) address.get("line2"));
            ps.setString(i++, (String) address.get("landmark"));
            ps.setString(i++, (String) address.get("city"));
            ps.setString(i++, (String) address.get("state"));
            ps.setString(i++, (String) address.get("postal_code"));
            ps.setString(i++, (String) address.get("country"));
            ps.setObject(i++, estimated);
            ps.setObject(i++, utc(placedAt));
            ps.setObject(i++, finalDelivered == null ? null : utc(finalDelivered));
            ps.setObject(i++, finalCancelled == null ? null : utc(finalCancelled));
            ps.setString(i++, finalReason);
            ps.setObject(i++, utc(createdAt));
            ps.setObject(i, utc(updatedAt));
            return ps;
        }, keys);
        long orderId = keys.getKey().longValue();

        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            PricingLine priced = pricing.get(index);
            String image = line.product().getImages().isEmpty() ? null : line.product().getImages().getFirst().getThumbnailUrl();
            jdbc.update("""
                    insert into order_items (order_id, product_id, variant_id, product_name, variant_name, sku, image_url,
                      unit_mrp, unit_price, quantity, line_total, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, orderId, line.product().getId(), line.variant().getId(), line.product().getName(),
                    line.variant().getName(), line.variant().getSku(), image, priced.unitMrp(), priced.unitPrice(),
                    line.quantity(), Money.of(priced.lineTotal()), utc(placedAt));
            jdbc.update("insert into user_activities (user_id, event_type, product_id, category_id, metadata, created_at) "
                            + "values (?, 'PURCHASE', ?, ?, ?, ?)", customer.getId(), line.product().getId(),
                    line.product().getCategory().getId(), "{\"orderId\":" + orderId + ",\"quantity\":" + line.quantity() + "}",
                    utc(placedAt));
            if (finalDelivered != null) {
                deliveredPurchases.computeIfAbsent(customer.getId(), key -> new ArrayList<>())
                        .add(new long[] {line.product().getId(), orderId, finalDelivered.toEpochMilli()});
            }
        }
        jdbc.batchUpdate("insert into order_status_history (order_id, from_status, to_status, changed_by_user_id, note, "
                + "created_at) values (?, ?, ?, ?, ?, ?)", history.stream().map(entry -> new Object[] {orderId,
                entry[0], entry[1], entry[2], entry[3], utc((Instant) entry[4])}).toList());

        String paymentStatus;
        Instant paidAt = null;
        if (online) {
            paymentStatus = "CANCELLED".equals(status) ? "REFUNDED" : "PAID";
            paidAt = placedAt;
        } else if ("DELIVERED".equals(status)) {
            paymentStatus = "PAID";
            paidAt = deliveredAt;
        } else {
            paymentStatus = "CANCELLED".equals(status) ? "CANCELLED" : "PENDING";
        }
        String seedRef = Long.toString(orderId, 36).toUpperCase();
        jdbc.update("""
                insert into payments (order_id, method, status, amount, currency, razorpay_order_id, razorpay_payment_id,
                  provider_method, refund_id, refunded_amount, paid_at, refunded_at, collected_by_user_id, version,
                  created_at, updated_at) values (?, ?, ?, ?, 'INR', ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """, orderId, paymentMethod, paymentStatus, quote.total(),
                online ? "order_SEED" + seedRef : null, online ? "pay_SEED" + seedRef : null,
                online ? pickOne(random, "upi", "upi", "card", "netbanking", "wallet") : null,
                "REFUNDED".equals(paymentStatus) ? "rfnd_SEED" + seedRef : null,
                "REFUNDED".equals(paymentStatus) ? quote.total() : null,
                paidAt == null ? null : utc(paidAt), "REFUNDED".equals(paymentStatus) ? utc(finalCancelled) : null,
                !online && "PAID".equals(paymentStatus) ? adminId : null, utc(createdAt), utc(updatedAt));
        if (useCoupon && !"CANCELLED".equals(status)) {
            festiveUses.merge(customer.getId(), 1, Integer::sum);
            jdbc.update("insert into coupon_usages (coupon_id, user_id, order_id, discount_amount, created_at) "
                    + "values (?, ?, ?, ?, ?)", festiveCoupon, customer.getId(), orderId, quote.couponDiscount(), utc(placedAt));
            jdbc.update("update coupons set used_count = used_count + 1 where id = ?", festiveCoupon);
        }
        return orderId;
    }

    private int insertReviews(Random random, Map<Long, List<long[]>> purchases, SeedCatalog catalog,
                              Map<String, Product> products, Instant now) {
        Map<Long, SeedCatalog.Product> sourceById = new HashMap<>();
        catalog.products().forEach(source -> sourceById.put(products.get(source.name()).getId(), source));
        Set<String> reviewed = new HashSet<>();
        List<Object[]> rows = new ArrayList<>();
        purchases.forEach((userId, items) -> {
            for (long[] item : items) {
                String key = userId + ":" + item[0];
                if (reviewed.contains(key) || random.nextDouble() > 0.42) {
                    continue;
                }
                reviewed.add(key);
                SeedCatalog.Product source = sourceById.get(item[0]);
                int rating = (int) Math.max(1, Math.min(5, Math.round(source.quality() + random.nextGaussian() * 0.75)));
                Instant createdAt = Instant.ofEpochMilli(item[2]).plus(Duration.ofHours(12 + random.nextInt(24 * 9)));
                if (createdAt.isAfter(now)) {
                    createdAt = now.minus(Duration.ofMinutes(30));
                }
                String[] text = SeedReviewText.forRating(random, rating, source.category());
                rows.add(new Object[] {item[0], userId, item[1], rating, text[0], text[1], utc(createdAt), utc(createdAt)});
            }
        });
        jdbc.batchUpdate("insert into reviews (product_id, user_id, order_id, rating, title, comment, status, created_at, "
                + "updated_at) values (?, ?, ?, ?, ?, ?, 'PUBLISHED', ?, ?)", rows);
        return rows.size();
    }

    /** Product views, searches, carts, wishlists and browsing signals that feed recommendations and analytics. */
    private void insertBehaviour(Random random, SeedCatalog catalog, Map<String, Product> products,
                                 List<User> customers, Instant now) {
        List<Object[]> views = new ArrayList<>();
        List<SeedCatalog.Product> sources = catalog.products();
        for (int daysAgo = 60; daysAgo >= 0; daysAgo--) {
            double t = 1.0 - daysAgo / (double) DAYS;
            for (SeedCatalog.Product source : sources) {
                int count = poisson(random, source.demand() * trend(source.trend(), 0.5 + t / 2, daysAgo) * 2.2);
                Product product = products.get(source.name());
                for (int i = 0; i < count; i++) {
                    Instant at = now.minus(Duration.ofDays(daysAgo)).minus(Duration.ofMinutes(random.nextInt(24 * 60)));
                    Long userId = random.nextDouble() < 0.25 ? customers.get(random.nextInt(customers.size())).getId() : null;
                    views.add(new Object[] {product.getId(), userId, utc(at)});
                }
            }
        }
        jdbc.batchUpdate("insert into product_views (product_id, user_id, created_at) values (?, ?, ?)", views);

        String[] queries = {"gaming headphones under 5000", "wireless earbuds", "4k monitor", "power bank 20000mah",
                "mechanical keyboard", "5g phone under 20000", "laptop for college", "smartwatch with gps",
                "usb c hub", "gaming mouse", "noise cancelling headphones", "tablet for kids", "65w charger",
                "portable ssd", "ultrawide monitor"};
        List<Object[]> searches = new ArrayList<>();
        List<Object[]> activities = new ArrayList<>();
        Map<String, List<Product>> byCategory = new HashMap<>();
        products.values().forEach(product -> byCategory.computeIfAbsent(product.getCategory().getSlug(),
                key -> new ArrayList<>()).add(product));
        List<String> categorySlugs = new ArrayList<>(byCategory.keySet());
        java.util.Collections.sort(categorySlugs);
        for (User customer : customers) {
            List<String> interests = List.of(categorySlugs.get(random.nextInt(categorySlugs.size())),
                    categorySlugs.get(random.nextInt(categorySlugs.size())));
            for (int i = 0; i < 3 + random.nextInt(4); i++) {
                String query = queries[random.nextInt(queries.length)];
                Instant at = now.minus(Duration.ofMinutes(60L * 24 * random.nextInt(30) + random.nextInt(1440)));
                searches.add(new Object[] {customer.getId(), query, query, "SMART", 3 + random.nextInt(12), utc(at)});
                activities.add(new Object[] {customer.getId(), "SEARCH", null, null,
                        "{\"query\":\"" + query + "\",\"results\":8}", utc(at)});
            }
            for (String slug : interests) {
                List<Product> candidates = byCategory.get(slug);
                for (int i = 0; i < 4 + random.nextInt(5); i++) {
                    Product product = candidates.get(random.nextInt(candidates.size()));
                    Instant at = now.minus(Duration.ofMinutes(60L * 24 * random.nextInt(21) + random.nextInt(1440)));
                    activities.add(new Object[] {customer.getId(), "PRODUCT_VIEW", product.getId(),
                            product.getCategory().getId(), null, utc(at)});
                    if (random.nextDouble() < 0.25) {
                        activities.add(new Object[] {customer.getId(), "ADD_TO_CART", product.getId(),
                                product.getCategory().getId(), "{\"quantity\":1}", utc(at.plus(Duration.ofMinutes(3)))});
                    }
                }
                activities.add(new Object[] {customer.getId(), "CATEGORY_VIEW", null,
                        candidates.getFirst().getCategory().getId(), null,
                        utc(now.minus(Duration.ofDays(random.nextInt(20))))});
            }
            // Wishlist with a few products from the customer's interests.
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "insert into wishlists (user_id, created_at, updated_at) values (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, customer.getId());
                ps.setObject(2, utc(now.minus(Duration.ofDays(40))));
                ps.setObject(3, utc(now.minus(Duration.ofDays(2))));
                return ps;
            }, keys);
            long wishlistId = keys.getKey().longValue();
            Set<Long> saved = new HashSet<>();
            for (int i = 0; i < 2 + random.nextInt(4); i++) {
                List<Product> candidates = byCategory.get(interests.get(random.nextInt(interests.size())));
                Product product = candidates.get(random.nextInt(candidates.size()));
                if (saved.add(product.getId())) {
                    Instant at = now.minus(Duration.ofDays(1 + random.nextInt(30)));
                    jdbc.update("insert into wishlist_items (wishlist_id, product_id, created_at) values (?, ?, ?)",
                            wishlistId, product.getId(), utc(at));
                    activities.add(new Object[] {customer.getId(), "WISHLIST_ADD", product.getId(),
                            product.getCategory().getId(), null, utc(at)});
                }
            }
        }
        jdbc.batchUpdate("insert into search_history (user_id, query, normalized_query, search_type, result_count, "
                + "created_at) values (?, ?, ?, ?, ?, ?)", searches);
        jdbc.batchUpdate("insert into user_activities (user_id, event_type, product_id, category_id, metadata, created_at) "
                + "values (?, ?, ?, ?, ?, ?)", activities);
    }

    private void refreshAggregates() {
        jdbc.update("""
                update products p set sold_count = (select coalesce(sum(oi.quantity), 0) from order_items oi
                  join orders o on o.id = oi.order_id where oi.product_id = p.id and o.status <> 'CANCELLED')
                """);
        jdbc.update("""
                update products p set
                  average_rating = coalesce((select round(avg(r.rating), 2) from reviews r
                                             where r.product_id = p.id and r.status = 'PUBLISHED'), 0),
                  review_count = (select count(*) from reviews r where r.product_id = p.id and r.status = 'PUBLISHED')
                """);
    }

    /** Demand multiplier over time (t = 0 oldest day, 1 today). */
    static double trend(String trend, double t, int daysAgo) {
        return switch (trend) {
            case "rising" -> 0.35 + 1.45 * t;
            case "declining" -> 1.7 - 1.35 * t;
            case "spike" -> daysAgo <= 14 ? 2.8 : 0.7;
            default -> 1.0;
        };
    }

    private static String status(Random random, long ageHours) {
        double roll = random.nextDouble();
        if (ageHours > 24 * 12) {
            return roll < 0.93 ? "DELIVERED" : "CANCELLED";
        }
        if (ageHours > 24 * 7) {
            return roll < 0.70 ? "DELIVERED" : roll < 0.80 ? "OUT_FOR_DELIVERY" : roll < 0.95 ? "SHIPPED" : "CANCELLED";
        }
        if (ageHours > 24 * 3) {
            return roll < 0.40 ? "SHIPPED" : roll < 0.70 ? "PROCESSING" : roll < 0.90 ? "CONFIRMED" : "CANCELLED";
        }
        return roll < 0.50 ? "PLACED" : roll < 0.85 ? "CONFIRMED" : "PROCESSING";
    }

    static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static int poisson(Random random, double lambda) {
        double limit = Math.exp(-lambda);
        double product = random.nextDouble();
        int count = 0;
        while (product > limit) {
            count++;
            product *= random.nextDouble();
        }
        return count;
    }

    private static int pick(Random random, double[] weights, double total) {
        double target = random.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            target -= weights[i];
            if (target <= 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    private static double sum(double[] values) {
        double total = 0;
        for (double value : values) {
            total += value;
        }
        return total;
    }

    @SafeVarargs
    private static <T> T pickOne(Random random, T... options) {
        return options[random.nextInt(options.length)];
    }

    static boolean isWeekend(LocalDate day) {
        return day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}

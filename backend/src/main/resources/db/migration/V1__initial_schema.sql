-- ShopSmart initial schema (MySQL 8, InnoDB, utf8mb4).
-- Conventions: BIGINT surrogate keys, DATETIME(6) timestamps stored in UTC,
-- DECIMAL(12,2) for money, snake_case names, explicit FK / index names.

-- ---------------------------------------------------------------------------
-- Identity & access
-- ---------------------------------------------------------------------------
CREATE TABLE roles (
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    name VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name)
);

INSERT INTO roles (name) VALUES ('CUSTOMER'), ('ADMIN');

CREATE TABLE users (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    email                 VARCHAR(255)  NOT NULL,
    password_hash         VARCHAR(100)  NOT NULL,
    full_name             VARCHAR(120)  NOT NULL,
    phone                 VARCHAR(20)   NULL,
    avatar_url            VARCHAR(1024) NULL,
    status                VARCHAR(20)   NOT NULL,
    email_verified_at     DATETIME(6)   NULL,
    last_login_at         DATETIME(6)   NULL,
    failed_login_attempts INT           NOT NULL DEFAULT 0,
    locked_until          DATETIME(6)   NULL,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    INDEX idx_users_status (status),
    INDEX idx_users_created_at (created_at)
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

-- Opaque refresh tokens; only a SHA-256 hash is stored. Tokens rotate on use and
-- share a family id so reuse of a rotated token can revoke the whole family.
CREATE TABLE refresh_tokens (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    token_hash    VARCHAR(64)  NOT NULL,
    family_id     VARCHAR(36)  NOT NULL,
    expires_at    DATETIME(6)  NOT NULL,
    revoked_at    DATETIME(6)  NULL,
    created_by_ip VARCHAR(45)  NULL,
    user_agent    VARCHAR(255) NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    INDEX idx_refresh_tokens_user (user_id),
    INDEX idx_refresh_tokens_family (family_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Single-use tokens for email verification and password reset (hashed).
CREATE TABLE user_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    type       VARCHAR(30) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_tokens_hash UNIQUE (token_hash),
    INDEX idx_user_tokens_user_type (user_id, type),
    CONSTRAINT fk_user_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE addresses (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    label       VARCHAR(30)  NULL,
    full_name   VARCHAR(120) NOT NULL,
    phone       VARCHAR(20)  NOT NULL,
    line1       VARCHAR(200) NOT NULL,
    line2       VARCHAR(200) NULL,
    landmark    VARCHAR(120) NULL,
    city        VARCHAR(80)  NOT NULL,
    state       VARCHAR(80)  NOT NULL,
    postal_code VARCHAR(10)  NOT NULL,
    country     VARCHAR(2)   NOT NULL DEFAULT 'IN',
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_addresses_user (user_id),
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- Catalog
-- ---------------------------------------------------------------------------
CREATE TABLE categories (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(80)   NOT NULL,
    slug        VARCHAR(100)  NOT NULL,
    description VARCHAR(500)  NULL,
    image_url   VARCHAR(1024) NULL,
    sort_order  INT           NOT NULL DEFAULT 0,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_name UNIQUE (name),
    CONSTRAINT uk_categories_slug UNIQUE (slug)
);

CREATE TABLE brands (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(80)   NOT NULL,
    slug        VARCHAR(100)  NOT NULL,
    description VARCHAR(500)  NULL,
    logo_url    VARCHAR(1024) NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_brands_name UNIQUE (name),
    CONSTRAINT uk_brands_slug UNIQUE (slug)
);

-- price/discount_percent are the product's list price (MRP) and discount.
-- sale_price is derived: the lowest effective selling price across the product's
-- active variants. It is maintained by the application so listings can filter and
-- sort by price with an index instead of aggregating variants per query.
-- search_text is a derived, lower-cased search document (name, brand, category,
-- tags, descriptions, SKUs) used by keyword search.
CREATE TABLE products (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    name              VARCHAR(200)  NOT NULL,
    slug              VARCHAR(220)  NOT NULL,
    brand_id          BIGINT        NOT NULL,
    category_id       BIGINT        NOT NULL,
    short_description VARCHAR(300)  NULL,
    description       TEXT          NOT NULL,
    price             DECIMAL(12, 2) NOT NULL,
    discount_percent  DECIMAL(5, 2) NOT NULL DEFAULT 0,
    sale_price        DECIMAL(12, 2) NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    featured          BOOLEAN       NOT NULL DEFAULT FALSE,
    average_rating    DECIMAL(3, 2) NOT NULL DEFAULT 0,
    review_count      INT           NOT NULL DEFAULT 0,
    sold_count        INT           NOT NULL DEFAULT 0,
    search_text       TEXT          NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_products_slug UNIQUE (slug),
    CONSTRAINT ck_products_price CHECK (price >= 0 AND sale_price >= 0),
    CONSTRAINT ck_products_discount CHECK (discount_percent >= 0 AND discount_percent <= 95),
    CONSTRAINT ck_products_rating CHECK (average_rating >= 0 AND average_rating <= 5),
    INDEX idx_products_category_status (category_id, status),
    INDEX idx_products_brand (brand_id),
    INDEX idx_products_status_created (status, created_at),
    INDEX idx_products_status_sale_price (status, sale_price),
    INDEX idx_products_status_sold (status, sold_count),
    CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands (id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id)
);

CREATE TABLE product_tags (
    product_id BIGINT      NOT NULL,
    tag        VARCHAR(40) NOT NULL,
    PRIMARY KEY (product_id, tag),
    INDEX idx_product_tags_tag (tag),
    CONSTRAINT fk_product_tags_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

CREATE TABLE product_specifications (
    product_id BIGINT       NOT NULL,
    position   INT          NOT NULL,
    name       VARCHAR(80)  NOT NULL,
    value      VARCHAR(255) NOT NULL,
    PRIMARY KEY (product_id, position),
    CONSTRAINT fk_product_specs_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

-- Image binaries live in file/object storage; only URLs and metadata are stored here.
CREATE TABLE product_images (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    product_id    BIGINT        NOT NULL,
    url           VARCHAR(1024) NOT NULL,
    thumbnail_url VARCHAR(1024) NULL,
    alt_text      VARCHAR(255)  NULL,
    sort_order    INT           NOT NULL DEFAULT 0,
    storage_key   VARCHAR(255)  NULL,
    content_type  VARCHAR(50)   NULL,
    size_bytes    BIGINT        NULL,
    width         INT           NULL,
    height        INT           NULL,
    created_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_product_images_product (product_id, sort_order),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

-- Every product has at least one variant; SKUs live only here (globally unique).
CREATE TABLE product_variants (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    product_id     BIGINT         NOT NULL,
    sku            VARCHAR(64)    NOT NULL,
    name           VARCHAR(120)   NOT NULL,
    price_override DECIMAL(12, 2) NULL,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    sort_order     INT            NOT NULL DEFAULT 0,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_variants_sku UNIQUE (sku),
    CONSTRAINT ck_product_variants_price CHECK (price_override IS NULL OR price_override >= 0),
    INDEX idx_product_variants_product (product_id),
    CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE TABLE product_variant_attributes (
    variant_id BIGINT      NOT NULL,
    name       VARCHAR(40) NOT NULL,
    value      VARCHAR(80) NOT NULL,
    PRIMARY KEY (variant_id, name),
    CONSTRAINT fk_variant_attributes_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE CASCADE
);

-- quantity = units on hand that are not yet sold; reserved = units held for online
-- orders awaiting payment. Available to sell = quantity - reserved.
CREATE TABLE inventory (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    variant_id          BIGINT      NOT NULL,
    quantity            INT         NOT NULL DEFAULT 0,
    reserved            INT         NOT NULL DEFAULT 0,
    low_stock_threshold INT         NOT NULL DEFAULT 5,
    version             BIGINT      NOT NULL DEFAULT 0,
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventory_variant UNIQUE (variant_id),
    CONSTRAINT ck_inventory_quantity CHECK (quantity >= 0),
    CONSTRAINT ck_inventory_reserved CHECK (reserved >= 0 AND reserved <= quantity),
    CONSTRAINT ck_inventory_threshold CHECK (low_stock_threshold >= 0),
    CONSTRAINT fk_inventory_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- Cart & wishlist
-- ---------------------------------------------------------------------------
CREATE TABLE carts (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    coupon_code VARCHAR(40) NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_carts_user UNIQUE (user_id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE cart_items (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    cart_id    BIGINT      NOT NULL,
    variant_id BIGINT      NOT NULL,
    quantity   INT         NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cart_items_cart_variant UNIQUE (cart_id, variant_id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity > 0),
    INDEX idx_cart_items_variant (variant_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE CASCADE
);

CREATE TABLE wishlists (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_wishlists_user UNIQUE (user_id),
    CONSTRAINT fk_wishlists_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE wishlist_items (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    wishlist_id BIGINT      NOT NULL,
    product_id  BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_wishlist_items UNIQUE (wishlist_id, product_id),
    INDEX idx_wishlist_items_product (product_id),
    CONSTRAINT fk_wishlist_items_wishlist FOREIGN KEY (wishlist_id) REFERENCES wishlists (id) ON DELETE CASCADE,
    CONSTRAINT fk_wishlist_items_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- Coupons
-- ---------------------------------------------------------------------------
CREATE TABLE coupons (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    code                VARCHAR(40)    NOT NULL,
    description         VARCHAR(255)   NULL,
    discount_type       VARCHAR(20)    NOT NULL,
    discount_value      DECIMAL(12, 2) NOT NULL,
    min_order_amount    DECIMAL(12, 2) NULL,
    max_discount_amount DECIMAL(12, 2) NULL,
    starts_at           DATETIME(6)    NULL,
    expires_at          DATETIME(6)    NULL,
    usage_limit         INT            NULL,
    per_user_limit      INT            NULL,
    used_count          INT            NOT NULL DEFAULT 0,
    active              BOOLEAN        NOT NULL DEFAULT TRUE,
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_coupons_code UNIQUE (code),
    CONSTRAINT ck_coupons_value CHECK (discount_value > 0),
    CONSTRAINT ck_coupons_used CHECK (used_count >= 0)
);

-- ---------------------------------------------------------------------------
-- Orders & payments
-- ---------------------------------------------------------------------------
CREATE TABLE orders (
    id                      BIGINT         NOT NULL AUTO_INCREMENT,
    order_number            VARCHAR(24)    NOT NULL,
    user_id                 BIGINT         NOT NULL,
    status                  VARCHAR(30)    NOT NULL,
    payment_method          VARCHAR(20)    NOT NULL,
    mrp_total               DECIMAL(12, 2) NOT NULL,
    subtotal                DECIMAL(12, 2) NOT NULL,
    coupon_code             VARCHAR(40)    NULL,
    coupon_discount         DECIMAL(12, 2) NOT NULL DEFAULT 0,
    shipping_fee            DECIMAL(12, 2) NOT NULL DEFAULT 0,
    tax_rate                DECIMAL(5, 4)  NOT NULL,
    tax_inclusive           BOOLEAN        NOT NULL,
    tax_amount              DECIMAL(12, 2) NOT NULL DEFAULT 0,
    total_amount            DECIMAL(12, 2) NOT NULL,
    currency                VARCHAR(3)     NOT NULL,
    ship_full_name          VARCHAR(120)   NOT NULL,
    ship_phone              VARCHAR(20)    NOT NULL,
    ship_line1              VARCHAR(200)   NOT NULL,
    ship_line2              VARCHAR(200)   NULL,
    ship_landmark           VARCHAR(120)   NULL,
    ship_city               VARCHAR(80)    NOT NULL,
    ship_state              VARCHAR(80)    NOT NULL,
    ship_postal_code        VARCHAR(10)    NOT NULL,
    ship_country            VARCHAR(2)     NOT NULL,
    customer_note           VARCHAR(500)   NULL,
    idempotency_key         VARCHAR(64)    NULL,
    estimated_delivery_date DATE           NULL,
    placed_at               DATETIME(6)    NULL,
    delivered_at            DATETIME(6)    NULL,
    cancelled_at            DATETIME(6)    NULL,
    cancellation_reason     VARCHAR(255)   NULL,
    version                 BIGINT         NOT NULL DEFAULT 0,
    created_at              DATETIME(6)    NOT NULL,
    updated_at              DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_orders_order_number UNIQUE (order_number),
    CONSTRAINT uk_orders_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_orders_amounts CHECK (subtotal >= 0 AND coupon_discount >= 0 AND shipping_fee >= 0
        AND tax_amount >= 0 AND total_amount >= 0),
    INDEX idx_orders_user_created (user_id, created_at),
    INDEX idx_orders_status_created (status, created_at),
    INDEX idx_orders_placed_at (placed_at),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Order lines snapshot product data at purchase time so later catalog edits never
-- change historical orders.
CREATE TABLE order_items (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    order_id     BIGINT         NOT NULL,
    product_id   BIGINT         NOT NULL,
    variant_id   BIGINT         NOT NULL,
    product_name VARCHAR(200)   NOT NULL,
    variant_name VARCHAR(120)   NOT NULL,
    sku          VARCHAR(64)    NOT NULL,
    image_url    VARCHAR(1024)  NULL,
    unit_mrp     DECIMAL(12, 2) NOT NULL,
    unit_price   DECIMAL(12, 2) NOT NULL,
    quantity     INT            NOT NULL,
    line_total   DECIMAL(12, 2) NOT NULL,
    created_at   DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    INDEX idx_order_items_order (order_id),
    INDEX idx_order_items_product (product_id),
    INDEX idx_order_items_variant (variant_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_order_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id)
);

CREATE TABLE order_status_history (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    order_id           BIGINT       NOT NULL,
    from_status        VARCHAR(30)  NULL,
    to_status          VARCHAR(30)  NOT NULL,
    changed_by_user_id BIGINT       NULL,
    note               VARCHAR(500) NULL,
    created_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_order_status_history_order (order_id, created_at),
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_status_history_user FOREIGN KEY (changed_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE coupon_usages (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    coupon_id       BIGINT         NOT NULL,
    user_id         BIGINT         NOT NULL,
    order_id        BIGINT         NOT NULL,
    discount_amount DECIMAL(12, 2) NOT NULL,
    created_at      DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_coupon_usages_order UNIQUE (order_id),
    INDEX idx_coupon_usages_coupon_user (coupon_id, user_id),
    CONSTRAINT fk_coupon_usages_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id),
    CONSTRAINT fk_coupon_usages_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_coupon_usages_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE TABLE payments (
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    order_id             BIGINT         NOT NULL,
    method               VARCHAR(20)    NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    amount               DECIMAL(12, 2) NOT NULL,
    currency             VARCHAR(3)     NOT NULL,
    razorpay_order_id    VARCHAR(64)    NULL,
    razorpay_payment_id  VARCHAR(64)    NULL,
    provider_method      VARCHAR(30)    NULL,
    failure_reason       VARCHAR(255)   NULL,
    refund_id            VARCHAR(64)    NULL,
    refunded_amount      DECIMAL(12, 2) NULL,
    paid_at              DATETIME(6)    NULL,
    failed_at            DATETIME(6)    NULL,
    refunded_at          DATETIME(6)    NULL,
    collected_by_user_id BIGINT         NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    created_at           DATETIME(6)    NOT NULL,
    updated_at           DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_order UNIQUE (order_id),
    CONSTRAINT uk_payments_razorpay_order UNIQUE (razorpay_order_id),
    CONSTRAINT uk_payments_razorpay_payment UNIQUE (razorpay_payment_id),
    INDEX idx_payments_status (status),
    INDEX idx_payments_method_status (method, status),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_payments_collected_by FOREIGN KEY (collected_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);

-- Processed payment-provider webhook events. The unique key makes webhook handling
-- idempotent: a redelivered event is recognised and ignored.
CREATE TABLE payment_events (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    provider            VARCHAR(20)  NOT NULL,
    event_id            VARCHAR(100) NOT NULL,
    event_type          VARCHAR(60)  NOT NULL,
    razorpay_order_id   VARCHAR(64)  NULL,
    razorpay_payment_id VARCHAR(64)  NULL,
    outcome             VARCHAR(20)  NOT NULL,
    detail              VARCHAR(255) NULL,
    received_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_events_provider_event UNIQUE (provider, event_id),
    INDEX idx_payment_events_razorpay_order (razorpay_order_id)
);

-- Inventory history (every change to quantity or reservations).
CREATE TABLE stock_movements (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    variant_id      BIGINT       NOT NULL,
    type            VARCHAR(30)  NOT NULL,
    quantity_change INT          NOT NULL,
    reserved_change INT          NOT NULL,
    quantity_after  INT          NOT NULL,
    reserved_after  INT          NOT NULL,
    order_id        BIGINT       NULL,
    actor_user_id   BIGINT       NULL,
    note            VARCHAR(255) NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_stock_movements_variant (variant_id, created_at),
    INDEX idx_stock_movements_order (order_id),
    CONSTRAINT fk_stock_movements_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_movements_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE SET NULL,
    CONSTRAINT fk_stock_movements_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
);

-- ---------------------------------------------------------------------------
-- Reviews
-- ---------------------------------------------------------------------------
CREATE TABLE reviews (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    product_id      BIGINT        NOT NULL,
    user_id         BIGINT        NOT NULL,
    order_id        BIGINT        NULL,
    rating          INT           NOT NULL,
    title           VARCHAR(120)  NOT NULL,
    comment         VARCHAR(2000) NULL,
    status          VARCHAR(20)   NOT NULL,
    moderation_note VARCHAR(255)  NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reviews_user_product UNIQUE (user_id, product_id),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    INDEX idx_reviews_product_status (product_id, status, created_at),
    INDEX idx_reviews_status_created (status, created_at),
    CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE SET NULL
);

-- ---------------------------------------------------------------------------
-- Notifications
-- ---------------------------------------------------------------------------
CREATE TABLE notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(40)  NOT NULL,
    title      VARCHAR(160) NOT NULL,
    message    VARCHAR(500) NOT NULL,
    link       VARCHAR(255) NULL,
    read_at    DATETIME(6)  NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_notifications_user_created (user_id, created_at),
    INDEX idx_notifications_user_read (user_id, read_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- Behavioural data (recommendations & analytics)
-- ---------------------------------------------------------------------------
-- Every search, including anonymous ones (user_id NULL), for search analytics and
-- a signed-in user's recent searches.
CREATE TABLE search_history (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NULL,
    query            VARCHAR(255) NOT NULL,
    normalized_query VARCHAR(255) NOT NULL,
    search_type      VARCHAR(20)  NOT NULL,
    result_count     INT          NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_search_history_user (user_id, created_at),
    INDEX idx_search_history_created (created_at),
    INDEX idx_search_history_query (normalized_query),
    CONSTRAINT fk_search_history_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Every product detail view, including anonymous ones, for popularity/conversion.
CREATE TABLE product_views (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    product_id BIGINT      NOT NULL,
    user_id    BIGINT      NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_product_views_product (product_id, created_at),
    INDEX idx_product_views_created (created_at),
    INDEX idx_product_views_user (user_id, created_at),
    CONSTRAINT fk_product_views_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_views_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Signed-in users' behavioural event log (the recommendation engine's signals).
-- metadata holds small JSON documents (e.g. {"query":"...","quantity":2}).
CREATE TABLE user_activities (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    user_id     BIGINT        NOT NULL,
    event_type  VARCHAR(30)   NOT NULL,
    product_id  BIGINT        NULL,
    category_id BIGINT        NULL,
    metadata    VARCHAR(1000) NULL,
    created_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_user_activities_user (user_id, created_at),
    INDEX idx_user_activities_type (event_type, created_at),
    INDEX idx_user_activities_product (product_id),
    CONSTRAINT fk_user_activities_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_activities_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_activities_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL
);

-- Cached personalised recommendations. Rows are replaced when recomputed and
-- invalidated when the user produces new activity.
CREATE TABLE recommendations (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    product_id   BIGINT       NOT NULL,
    score        DOUBLE       NOT NULL,
    reason       VARCHAR(255) NULL,
    algorithm    VARCHAR(40)  NOT NULL,
    generated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_recommendations_user_product UNIQUE (user_id, product_id),
    INDEX idx_recommendations_user_score (user_id, score),
    CONSTRAINT fk_recommendations_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendations_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- Administration
-- ---------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT        NULL,
    actor_email   VARCHAR(255)  NULL,
    action        VARCHAR(60)   NOT NULL,
    entity_type   VARCHAR(40)   NULL,
    entity_id     VARCHAR(64)   NULL,
    details       VARCHAR(2000) NULL,
    ip_address    VARCHAR(45)   NULL,
    created_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_audit_logs_created (created_at),
    INDEX idx_audit_logs_entity (entity_type, entity_id),
    INDEX idx_audit_logs_actor (actor_user_id, created_at),
    INDEX idx_audit_logs_action (action, created_at),
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
);

-- Single-row store configuration editable by admins at runtime.
CREATE TABLE store_settings (
    id                              BIGINT         NOT NULL,
    store_name                      VARCHAR(80)    NOT NULL,
    support_email                   VARCHAR(255)   NOT NULL,
    currency                        VARCHAR(3)     NOT NULL,
    tax_rate                        DECIMAL(5, 4)  NOT NULL,
    prices_include_tax              BOOLEAN        NOT NULL,
    shipping_fee                    DECIMAL(12, 2) NOT NULL,
    free_shipping_threshold         DECIMAL(12, 2) NOT NULL,
    default_low_stock_threshold     INT            NOT NULL,
    cod_enabled                     BOOLEAN        NOT NULL,
    cod_max_order_amount            DECIMAL(12, 2) NULL,
    razorpay_enabled                BOOLEAN        NOT NULL,
    pending_payment_timeout_minutes INT            NOT NULL,
    updated_by_user_id              BIGINT         NULL,
    updated_at                      DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_store_settings_singleton CHECK (id = 1),
    CONSTRAINT ck_store_settings_tax CHECK (tax_rate >= 0 AND tax_rate < 1),
    CONSTRAINT fk_store_settings_user FOREIGN KEY (updated_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);

INSERT INTO store_settings (id, store_name, support_email, currency, tax_rate, prices_include_tax, shipping_fee,
                            free_shipping_threshold, default_low_stock_threshold, cod_enabled, cod_max_order_amount,
                            razorpay_enabled, pending_payment_timeout_minutes, updated_at)
VALUES (1, 'ShopSmart', 'support@shopsmart.example', 'INR', 0.1800, TRUE, 49.00, 499.00, 5, TRUE, 50000.00, TRUE, 30,
        UTC_TIMESTAMP(6));

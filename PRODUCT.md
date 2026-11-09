# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **Shoppers**: Indian electronics consumers shopping for personal, work, and entertainment technology (smartphones, audio, computing, wearables, accessories). They need transparent specifications, dependable in-stock availability, fast search, familiar payment methods (UPI, cards, Netbanking, Cash on Delivery), and live delivery visibility.
- **Store Administrators & Operations Staff**: Catalog managers, fulfillment specialists, and store owners operating the platform. They require fast inventory adjustment, order pipeline tracking, review moderation, coupon administration, and actionable analytics with demand forecasting.

## Product Purpose

ShopSmart is an AI-powered e-commerce and order management platform built specifically for Indian consumer electronics retail. It pairs intelligent discovery (natural language search and explainable recommendations) with transactional integrity (concurrent inventory reservation, Razorpay and COD payment workflows, and real-time STOMP order updates) and operational control. Success means fast product discovery, trustworthy checkout without inventory contention, and end-to-end fulfillment visibility.

## Positioning

Trust, speed, and transparency in electronics commerce:
- Natural-language search that parses budget, brands, categories, and technical features into structured filters combined with semantic ranking.
- Transparent recommendations with explicit rationale ("Because you viewed...", "Trending now") rather than opaque black-box suggestions.
- Concurrency-safe checkout with database-level row locking and automatic payment reconciliation.
- Real-time order tracking timeline over WebSockets for both customer reassurance and administrative dispatch.

## Operating Context

- **Market & Locale**: Indian retail ecosystem with INR (₹) currency formatting, GST calculations, Indian postal address structures, and support for local payment patterns (Razorpay UPI, cards, net banking, wallets, and Cash on Delivery).
- **Environment**: Multi-tab desktop and mobile web experiences synchronized in real time via STOMP WebSockets, backed by memory access tokens and HttpOnly refresh cookies.
- **Operational Cadence**: Real-time push updates for order status transitions, low-stock inventory alerts, and periodic background reconciliation jobs for payment and catalog indexing.

## Capabilities and Constraints

- **Confirmed Capabilities**:
  - Storefront catalog with category/brand filtering, specification comparisons, and multi-variant pricing/stock.
  - Natural-language search with debounced autocomplete and semantic ranking via MiniLM sentence transformers (with TF-IDF and keyword fallbacks).
  - Personalized and item-to-item recommendations with recency decay and previous-purchase exclusions.
  - Full checkout lifecycle with coupon validation, address management, and server-side pricing verification.
  - Payment integration with Razorpay (Orders API, signature verification, webhooks, reconciliation) and Cash on Delivery.
  - WebSocket-powered live order tracking timeline and in-app notifications.
  - Admin management suite: catalog, variants, inventory movement history, fulfillment workflow, customer management, coupons, and review moderation.
  - Historical business analytics and ML-assisted demand forecasting with 80% prediction intervals and insufficient-data safeguards.
- **Technical & Product Constraints**:
  - Single-instance backend architecture with in-memory STOMP broker and rate limiting.
  - Local disk image storage for product uploads.
  - Reviews restricted strictly to verified purchasers.
  - Account requirement for checkout (no anonymous guest checkout).

## Brand Commitments

- **Name**: ShopSmart ("Smart electronics, delivered").
- **Voice & Tone**: Direct, reliable, knowledgeable, and tech-forward without hyperbole.
- **Core Identity Elements**:
  - Dominant Brand Accent: Warm amber/orange (`--color-brand-500: #f97316`, `--color-brand-600: #ea580c`).
  - Ink & Surfaces: Deep slate ink (`#0f172a`), clean stone neutral backgrounds (`bg-stone-50`), and white surface cards.
  - Typography: Inter Variable (`@fontsource-variable/inter`).

## Evidence on Hand

- Verified seed catalog containing 10 categories, 15 brands, 61 products with 79 variants, 5 coupons, and 120 days of historical transactions, customer interactions, reviews, and search records.
- Comprehensive technical documentation: [README.md](file:///d:/Real%20Time%20E-commerce%20&%20Order%20Management%20Platform/README.md), [ARCHITECTURE.md](file:///d:/Real%20Time%20E-commerce%20&%20Order%20Management%20Platform/ARCHITECTURE.md), [API.md](file:///d:/Real%20Time%20E-commerce%20&%20Order%20Management%20Platform/API.md), [DATABASE.md](file:///d:/Real%20Time%20E-commerce%20&%20Order%20Management%20Platform/DATABASE.md), [DEPLOYMENT.md](file:///d:/Real%20Time%20E-commerce%20&%20Order%20Management%20Platform/DEPLOYMENT.md), [SECURITY.md](file:///d:/Real%20Time%20E-commerce%20&%20Order%20Management%20Platform/SECURITY.md).
- *Absences to preserve*: No external shipping-carrier tracking APIs, multi-currency engines, or social OAuth providers exist; future UI work must not fabricate external carrier feeds or social logins.

## Product Principles

1. **Trust Over Hype**: Explain AI behavior openly (how a query was interpreted, why an item was recommended); never compromise pricing accuracy, stock truth, or delivery reality for promotional flair.
2. **Transactional Integrity Above All**: The server is the sole authority on inventory reservations, discounts, and order amounts; frontend interfaces reflect verified system states rather than optimistic assumptions.
3. **High-Scanability Operations**: Admin consoles prioritize high-density data tables, immediate status signaling, and efficient fulfillment workflows over decorative visuals.
4. **Frictionless Indian Commerce**: Prioritize intuitive Indian retail patterns including rupee formatting, UPI and COD flows, clean mobile responsiveness, and clear delivery timelines.

## Accessibility & Inclusion

- Baseline target of WCAG 2.1 AA compliance across customer storefront and admin console.
- Distinct high-contrast focus rings (`:focus-visible` with 2px brand outline and offset).
- Accessible form controls with explicit label association and server-driven inline validation messages.
- Semantic live regions for asynchronous WebSocket updates (order status changes and inventory alerts).

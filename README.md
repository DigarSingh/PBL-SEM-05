# ShopSmart

AI-powered e-commerce and order management platform for an Indian electronics store: a storefront
with natural-language search and personalised recommendations, a checkout with Razorpay and Cash on
Delivery, live order tracking over WebSockets, and an admin console for catalog, inventory, orders,
payments, customers, coupons, reviews and analytics with demand forecasting.

| Part | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1 (Web MVC, Data JPA/Hibernate 7, Security with JWT, WebSocket/STOMP, Validation, Actuator), MySQL 8, Flyway, Maven |
| Frontend | React 19, TypeScript, Vite, React Router, TanStack Query, React Hook Form + Zod, Tailwind CSS 4, STOMP client, Recharts |
| AI service | Python, FastAPI, sentence-transformers (MiniLM), scikit-learn, pandas, NumPy |
| Payments | Razorpay (Orders API, Checkout, signature verification, webhooks) and Cash on Delivery |
| Operations | Docker, Docker Compose, Nginx, GitHub Actions |

## Features

### Shoppers

- Browse by category and brand with filters (price, rating, discount, availability) and sorting.
- Smart search that understands queries such as *"gaming headphones under 5000 with wireless"*:
  budget, rating, brand and category become filters, while features and use cases steer an
  AI (semantic) ranking. The results page shows how the query was understood.
- Recommendations from views, searches, cart, wishlist and purchases: personalised picks,
  "similar products" (semantic) and trending items.
- Product variants (colour, storage, ...) with per-variant stock, reviews from verified buyers,
  wishlist, cart with coupons, saved addresses.
- Checkout with Razorpay (UPI, cards, net banking, wallets) or Cash on Delivery. Prices, discounts,
  tax, shipping and stock are always calculated and checked by the server.
- Order history with a live tracking timeline, in-app notifications and e-mails, cancellation
  (with automatic refunds for online payments), and retrying unpaid online orders.
- Accounts with e-mail verification, password reset, session refresh and sign-out everywhere on
  password change.

### Administrators

- Dashboard with live KPIs, charts, recent orders and low-stock alerts (pushed in real time).
- Products with variants, specifications, tags and images (upload or link), categories and brands.
- Orders: fulfilment workflow (confirm, process, ship, out for delivery, deliver), cancellation,
  COD cash collection, refund retries, full status history.
- Inventory with stock adjustments, alert thresholds and a movement history for every change.
- Customers (enable/disable), payments, coupons, review moderation, store settings, audit log.
- Analytics: sales, products, categories, customers and stock cover from real orders, plus
  historical insights and ML demand forecasts with prediction intervals, clearly separated.

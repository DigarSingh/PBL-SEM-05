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

Further reading: [ARCHITECTURE.md](ARCHITECTURE.md) · [API.md](API.md) · [DATABASE.md](DATABASE.md) ·
[SECURITY.md](SECURITY.md) · [DEPLOYMENT.md](DEPLOYMENT.md)

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

### AI feature behavior and APIs

- `GET /api/recommendations/for-you?limit=12` uses the signed-in shopper's recent interactions and
  order history; guests and shoppers without usable signals receive in-stock trending products.
  Previously purchased items are excluded from personalized picks. `GET /api/recommendations/similar/{id}`
  returns related products; both paths re-check product visibility and availability in Spring before
  returning cards.
- `GET /api/search?q=...` combines parsed budget/category/brand/rating/stock filters with semantic
  ranking. Spring selects eligible catalog candidates first, so AI ranking cannot bypass visibility
  or explicit filters. If the AI service is unavailable, Spring ranks those candidates by keyword
  and product signals. The search box debounces autocomplete and cancels stale HTTP requests.
- `GET /api/admin/analytics/insights?from=YYYY-MM-DD&to=YYYY-MM-DD` is admin-only and returns
  historical insights plus separately labelled forecasts. Sales and forecasts use placed orders
  whose status is not `PENDING_PAYMENT` or `CANCELLED`; COD is included once placed. Refunds in this
  lifecycle are attached to cancelled orders, which are excluded. Forecasts use up to 120 days of
  daily units and a recency-weighted weekly trend with an 80% interval; products below the minimum
  history requirements return `INSUFFICIENT_DATA` instead of a guessed value.
- FastAPI is private to the Compose network. Spring pulls active catalog text and pseudonymous,
  aggregated interactions over `/internal/ai/*`; it does not send names, e-mails, addresses, order
  notes or payment data. The internal key is shared only between the backend and AI service.
- MiniLM sentence-transformer embeddings are the default; set `AI_WITH_EMBEDDINGS=false` to build
  the smaller TF-IDF fallback image. Change `EMBEDDING_MODEL` at image build time to select another
  sentence-transformer. `EMBEDDING_BACKEND=auto` uses the configured model when available and falls
  back to TF-IDF otherwise. The in-memory index refreshes in the background and fingerprints its
  indexed product data so stock and popularity updates are included.
- `GET http://localhost:8000/health` (when running the AI service directly) reports the selected
  embedding backend and index status; it returns 503 until the first catalog index is ready. Search
  and recommendations continue with Spring fallbacks during that warm-up or an AI-service outage.

## Quick start

You need the secrets file first:

```bash
cp .env.example .env
# then edit .env: set DATABASE_PASSWORD, MYSQL_ROOT_PASSWORD, JWT_SECRET (32+ random characters),
# AI_SERVICE_API_KEY, SEED_ADMIN_PASSWORD and SEED_CUSTOMER_PASSWORD.
# Optional: RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET (test keys) to enable online payments.
```

Generate random values with `openssl rand -base64 48` (or any password manager). `.env` is
git-ignored; never commit it.

### Option A: Docker Compose

```bash
docker compose up --build
```

| URL | What |
| --- | --- |
| <http://localhost:3000> | Store and admin console (`/admin`) |
| <http://localhost:8080/swagger-ui.html> | API documentation (dev profile) |
| <http://localhost:8025> | Mailpit: every e-mail the app sends |

The first start builds all images (the AI image downloads CPU PyTorch and the MiniLM model; set
`AI_WITH_EMBEDDINGS=false` in `.env` for a much smaller image that uses TF-IDF instead) and seeds
demo data. MySQL is published on `localhost:3307` so it never collides with a local MySQL.

### Option B: run each service locally

Prerequisites: JDK 21, Node.js 22+, Python 3.12+ (3.14 tested), MySQL 8. For e-mail in
development run Mailpit (`docker run -p 1025:1025 -p 8025:8025 axllent/mailpit`) or any SMTP
server; without one, e-mails fail to send (logged) and new accounts cannot be verified. Set
`REQUIRE_EMAIL_VERIFICATION=false` in `.env` to skip verification for local testing.

1. **Database** (skip if you use `docker compose up mysql mailpit`):

   ```sql
   CREATE DATABASE shopsmart CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   CREATE USER 'shopsmart'@'%' IDENTIFIED BY '<DATABASE_PASSWORD from .env>';
   GRANT ALL PRIVILEGES ON shopsmart.* TO 'shopsmart'@'%';
   ```

   Point `DATABASE_URL` in `.env` at it (the example uses port 3307).

2. **Backend** (reads `../.env` automatically in the default `dev` profile; Flyway creates the
   schema and demo data is seeded on first start):

   ```bash
   cd backend
   ./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
   ```

3. **AI service** (optional; search and recommendations fall back to keyword and rule-based
   ranking without it):

   ```bash
   cd ai-service
   python -m venv .venv
   .venv/bin/pip install -r requirements.txt -r requirements-ml.txt   # Windows: .venv\Scripts\pip
   export AI_SERVICE_API_KEY=<same value as in .env> BACKEND_URL=http://localhost:8080
   .venv/bin/uvicorn app.main:app --port 8000
   ```

   PowerShell: `$env:AI_SERVICE_API_KEY = "<value>"; $env:BACKEND_URL = "http://localhost:8080"`.
   Omit `requirements-ml.txt` to use the lighter TF-IDF embeddings.

4. **Frontend** (the Vite dev server proxies `/api`, `/ws` and `/uploads` to the backend):

   ```bash
   cd frontend
   npm install
   npm run dev                     # http://localhost:5173
   ```

   The npm scripts call each tool's entry point through `node`, so they also work from folders
   whose path contains characters such as `&` on Windows.

## Demo accounts (dev profile only)

| Role | E-mail | Password |
| --- | --- | --- |
| Admin | `admin@shopsmart.test` (`SEED_ADMIN_EMAIL`) | value of `SEED_ADMIN_PASSWORD` |
| Customer | `customer@shopsmart.test` | value of `SEED_CUSTOMER_PASSWORD` |

Fourteen more customers (for example `kabir.malhotra@shopsmart.test`) share the customer
password. Seeding runs only when the catalog is empty and never in the `prod` profile. It creates
10 categories, 15 brands, 61 products with 79 variants, 5 coupons and 120 days of order, review,
view and search history so analytics, recommendations and forecasts have real data to work with.

## Razorpay test mode

1. Create test keys in the Razorpay dashboard (Settings > API Keys, test mode) and put them in
   `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET`. The key secret stays on the server; the browser only
   ever receives the key id.
2. Restart the backend. The checkout shows "Pay online" (with a *Razorpay test mode* badge) when
   keys are configured and online payments are enabled in *Admin > Settings*.
3. In the Razorpay window choose **Netbanking**, pick a bank and press **Success** (or
   **Failure**) on the demo bank page, or use a Razorpay test card.
4. The backend verifies the payment signature and confirms the payment with Razorpay's API before
   the order is placed. Webhooks are optional locally (see [DEPLOYMENT.md](DEPLOYMENT.md) for
   webhook setup); a reconciliation job settles payments whose callback never arrived.

## Tests

```bash
cd backend && ./mvnw verify        # unit + integration tests (Testcontainers MySQL, needs Docker)
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
cd ai-service && pip install -r requirements-dev.txt && ruff check . && pytest
```

Without Docker, point the backend integration tests at an empty MySQL database instead of
Testcontainers: `TEST_DATABASE_URL=jdbc:mysql://localhost:3307/shopsmart_test?...`,
`TEST_DATABASE_USERNAME`, `TEST_DATABASE_PASSWORD`.

| Suite | Scope |
| --- | --- |
| Backend | JWT, payments, checkout, access control including admin analytics, catalog, recommendations and customer data isolation, plus MySQL integration coverage |
| Frontend | Session refresh, search filters and request cancellation, recommendation loading/error/empty/success states, checkout, forms and components |
| AI service | API auth and validation, semantic ranking, recommendation relevance and exclusions, and forecast calculations and insufficient-history behavior |

## Project structure

```text
backend/      Spring Boot application (package per domain: auth, catalog, cart, order, payment, ...)
frontend/     React application (src/features/<area>, shared UI in src/components, API client in src/lib)
ai-service/   FastAPI service: embeddings, semantic ranking, recommendations, demand forecasting
docker-compose.yml, .github/workflows/ci.yml, .env.example
```

## Configuration

All configuration comes from environment variables; `.env.example` documents each one. The
most important:

| Variable | Purpose |
| --- | --- |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | MySQL connection |
| `JWT_SECRET` | HMAC key for access tokens (32+ characters) |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | Online payments (empty = COD only) |
| `MAIL_*` | SMTP for verification, password reset and order e-mails |
| `AI_SERVICE_URL`, `AI_SERVICE_API_KEY` | AI service location and shared secret |
| `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS` | Links in e-mails; allowed browser origins |
| `COOKIE_SECURE`, `REQUIRE_EMAIL_VERIFICATION` | Must stay `true` in production |

## Known limitations

- The STOMP broker and the rate limiter are in-memory, so the backend runs as a single instance
  (see [DEPLOYMENT.md](DEPLOYMENT.md) for what to change before scaling out).
- Uploaded images are stored on the local file system (a Docker volume), not object storage.
- Demand forecasts are a simple, explainable trend model; they do not know about holidays or
  promotions.
- There is no avatar upload, guest checkout, multi-currency or shipping-carrier integration.

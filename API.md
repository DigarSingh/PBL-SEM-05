# API

Interactive documentation (OpenAPI 3) is generated from the code:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Both are on in the `dev` profile and off in `prod` (`API_DOCS_ENABLED`). This page summarises the
conventions and the endpoints.

## Conventions

**Base path** `/api`. JSON in and out, UTF-8, money as JSON numbers in INR with two decimals,
timestamps as ISO-8601 UTC (`2026-09-25T08:30:00Z`), calendar days as `YYYY-MM-DD` (IST).

**Envelope**

```json
{ "success": true, "data": { ... }, "message": "Order placed" }
```

**Errors** use the HTTP status plus a machine-readable `code`:

```json
{
  "success": false,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "errors": [{ "field": "variants[0].sku", "message": "must not be blank" }],
  "details": {}
}
```

| Code | Status | Meaning |
| --- | --- | --- |
| `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `BAD_REQUEST` | 400 | invalid input (field errors in `errors`) |
| `INVALID_CREDENTIALS` | 401 (400 when changing the password) | wrong e-mail or password |
| `UNAUTHORIZED`, `INVALID_TOKEN` | 401 | not signed in, or the access token expired or is invalid |
| `EMAIL_NOT_VERIFIED`, `ACCOUNT_DISABLED`, `FORBIDDEN` | 403 | sign-in blocked or action not allowed |
| `NOT_FOUND` | 404 | unknown resource (or one you may not see) |
| `CONFLICT`, `DUPLICATE_RESOURCE`, `CONCURRENT_MODIFICATION`, `INSUFFICIENT_STOCK`, `INVALID_ORDER_TRANSITION` | 409 | the request conflicts with the current state |
| `PAYLOAD_TOO_LARGE` | 413 | upload too big |
| `BUSINESS_RULE_VIOLATION`, `INVALID_COUPON` | 422 | valid request that a business rule does not allow |
| `ACCOUNT_LOCKED` | 423 | too many failed sign-ins; try again later |
| `RATE_LIMITED` | 429 | slow down (`Retry-After` header) |
| `PAYMENT_VERIFICATION_FAILED` | 400 | the payment could not be verified |
| `PAYMENT_GATEWAY_ERROR` | 502 | Razorpay could not be reached |
| `PAYMENT_UNAVAILABLE` | 503 | online payments are not configured or disabled |

**Pagination**: `?page=0&size=20` (zero-based) returning
`{ items, page, size, totalElements, totalPages, hasNext }`.

**Authentication**: `Authorization: Bearer <access token>` from sign-in or refresh. The refresh
token is an HttpOnly cookie (`ss_refresh`, path `/api/auth`); refresh and sign-out also need the
header `X-Requested-With: XMLHttpRequest`.

**Rate limits** (per client IP): 10 requests/minute on sign-in, registration, verification and
password endpoints; 300/minute for the rest of the API.

## Endpoints

Auth column: **public**, **user** (any signed-in account), **admin**.

### Authentication `/api/auth`

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | `/register` | public | create a customer account `{fullName, email, password, phone?}`; sends a verification e-mail |
| POST | `/login` | public | `{email, password}` → access token + user; sets the refresh cookie |
| POST | `/refresh` | cookie | rotate the refresh cookie, new access token |
| POST | `/logout` | cookie | revoke the refresh token family, clear the cookie |
| POST | `/verify-email` | public | `{token}` from the e-mail |
| POST | `/resend-verification` | public | `{email}` (always answers the same) |
| POST | `/forgot-password` | public | `{email}` (always answers the same) |
| POST | `/reset-password` | public | `{token, newPassword}` |

### Account `/api/users/me`

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET / PATCH | `/` | user | profile; update `{fullName, phone}` |
| POST | `/password` | user | `{currentPassword, newPassword}`; signs out every session |
| GET / POST | `/addresses` | user | list / add delivery address |
| PUT / DELETE | `/addresses/{id}` | user | update / delete |
| POST | `/addresses/{id}/default` | user | make default |

### Catalog

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/api/products` | public | browse and keyword search: `q, category, brand[], minPrice, maxPrice, minRating, minDiscount, inStock, featured, sort, page, size`; `sort` = `relevance, newest, price_asc, price_desc, rating, popularity, discount` |
| GET | `/api/products/facets` | public | brand counts and price range for the same filters |
| GET | `/api/products/{id}`, `/api/products/slug/{slug}` | public | product details with variants, options, images, specifications |
| GET | `/api/products/{id}/related` | public | same category / brand |
| POST | `/api/products/{id}/views` | public | record a product page view |
| GET | `/api/categories`, `/api/categories/{slug}`, `/api/brands` | public | active categories and brands |
| GET | `/api/coupons` | public | currently available offers |
| GET | `/api/settings/public` | public | tax, shipping, COD and online-payment availability, Razorpay **key id** |

### Search and recommendations

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/api/search?q=...` | public | smart search; returns `interpretation`, `engine` (`semantic` or `keyword`), items with `matchReasons`; explicit filters override inferred ones |
| GET | `/api/search/suggestions?q=` | public | autocomplete (products, categories, brands) and recent searches |
| GET / DELETE | `/api/search/recent` | user | my recent searches |
| GET | `/api/recommendations/for-you` | public | personalised (signed in) or trending |
| GET | `/api/recommendations/similar/{productId}` | public | semantically similar products |
| GET | `/api/recommendations/trending` | public | recent sales, views and saves |
| POST | `/api/activity/category-views` | user | `{categoryId}` for recommendations |

### Cart, wishlist, reviews

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET / DELETE | `/api/cart` | user | cart with server-computed totals, stock issues, `canCheckout` / clear |
| POST | `/api/cart/items` | user | `{variantId, quantity}` (1-10) |
| PATCH / DELETE | `/api/cart/items/{itemId}` | user | `{quantity}` / remove |
| POST / DELETE | `/api/cart/coupon` | user | apply `{code}` / remove |
| GET | `/api/wishlist`, `/api/wishlist/ids` | user | saved products |
| POST | `/api/wishlist/items` | user | `{productId}` |
| DELETE | `/api/wishlist/items/{productId}` | user | remove |
| POST | `/api/wishlist/items/{productId}/move-to-cart` | user | move |
| GET | `/api/products/{id}/reviews`, `/reviews/summary` | public | published reviews (`sort=newest|highest|lowest`), rating distribution |
| GET | `/api/products/{id}/reviews/eligibility` | user | whether I can review (delivered purchase) |
| POST | `/api/products/{id}/reviews` | user | `{rating, title, comment?}` |
| PUT / DELETE | `/api/reviews/{id}` | user | edit / delete my review |

### Orders and payments

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | `/api/orders` | user | checkout `{addressId, paymentMethod: COD|RAZORPAY, customerNote?}` with header `Idempotency-Key` (8-64 characters, one per attempt). Returns the order and, for online payment, the Razorpay order to open |
| GET | `/api/orders?status=` | user | my orders |
| GET | `/api/orders/{id}` | user | order details, timeline, history, `canCancel`, `canPay`, `paymentDeadline` |
| POST | `/api/orders/{id}/cancel` | user | `{reason?}` before shipping; online payments are refunded |
| POST | `/api/orders/{id}/payment` | user | Razorpay order for paying (or retrying) an unpaid order |
| POST | `/api/payments/razorpay/verify` | user | `{orderId, razorpayOrderId, razorpayPaymentId, razorpaySignature}` |
| POST | `/api/payments/razorpay/failure` | user | report a failed attempt from Checkout |
| POST | `/api/payments/razorpay/webhook` | Razorpay | HMAC-verified (`X-Razorpay-Signature`), idempotent by event id |
| GET | `/api/notifications`, `/unread-count` | user | in-app notifications |
| POST | `/api/notifications/{id}/read`, `/read-all` | user | mark read |

### Administration `/api/admin` (admin role)

| Area | Endpoints |
| --- | --- |
| Dashboard & analytics | `GET /dashboard`; `GET /analytics/sales?from&to&granularity=day|week|month`, `/products`, `/categories`, `/customers`, `/inventory`, `/insights` |
| Products | `GET/POST /products`, `GET/PUT/DELETE /products/{id}` (delete = archive), `PATCH /products/{id}/status`, images: `POST /products/{id}/images` (multipart `files`, JPEG/PNG ≤ 5 MB), `POST /images/url`, `PUT /images/order`, `PATCH/DELETE /images/{imageId}` |
| Categories & brands | `GET/POST /categories`, `PUT/DELETE /categories/{id}`; same for `/brands` |
| Inventory | `GET /inventory?q&categoryId&lowStock&outOfStock`, `POST /inventory/{variantId}/adjustments` `{type: RESTOCK|ADJUSTMENT, quantity, note}`, `PATCH /inventory/{variantId}` `{lowStockThreshold}`, `GET /inventory/{variantId}/movements` |
| Orders | `GET /orders?status&paymentStatus&paymentMethod&includePending&q&from&to`, `GET /orders/{id}`, `PATCH /orders/{id}/status` `{status, note?}`, `POST /orders/{id}/cancel` `{reason?}` |
| Payments | `GET /payments?status&method&q`, `POST /payments/{id}/collect` (COD cash received), `POST /payments/{id}/refund` (retry a failed refund) |
| Customers | `GET /customers?q&status`, `GET /customers/{id}`, `PATCH /customers/{id}/status` `{status: ACTIVE|DISABLED}` |
| Coupons | `GET/POST /coupons`, `PUT/DELETE /coupons/{id}` |
| Reviews | `GET /reviews?status&rating&productId&q`, `PATCH /reviews/{id}` `{status: PUBLISHED|HIDDEN, note?}` |
| Settings & audit | `GET/PUT /settings`, `GET /audit-logs?action&entityType&actorUserId` |

## WebSocket (STOMP)

Endpoint `/ws` (native WebSocket). Send the access token in the `CONNECT` frame:
`Authorization: Bearer <token>`.

| Destination | Who may subscribe | Payload |
| --- | --- | --- |
| `/user/queue/orders` | the signed-in user | order updates for their orders |
| `/user/queue/notifications` | the signed-in user | new notifications |
| `/topic/orders/{orderId}` | the order's owner or an admin | updates for one order |
| `/topic/admin/orders` | admins | every order update |
| `/topic/admin/inventory` | admins | low-stock alerts |

Order update payload:

```json
{ "type": "ORDER_STATUS_CHANGED", "orderId": 686, "orderNumber": "SS260925-J6BTBM",
  "status": "SHIPPED", "statusLabel": "Shipped", "paymentStatus": "PAID",
  "message": "Your order SS260925-J6BTBM has been shipped.", "at": "2026-09-25T09:10:00Z" }
```

`type` is one of `ORDER_PLACED`, `ORDER_STATUS_CHANGED`, `PAYMENT_FAILED`, `PAYMENT_REFUNDED`.

## Service-to-service

The AI service reads `GET /internal/ai/catalog` and `GET /internal/ai/interactions?days=` with the
header `X-Internal-Api-Key`. The backend calls the AI service's `POST /v1/search/rank`,
`/v1/recommendations/similar`, `/v1/recommendations/user` and `/v1/forecast/demand` with the same
key. `/internal/**` is never exposed through the public Nginx proxy.

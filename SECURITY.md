# Security

This document describes how ShopSmart protects accounts, payments and data, and what operators
must do. Report vulnerabilities privately to the maintainers rather than in public issues.

## Authentication and sessions

- **Passwords**: BCrypt (cost 12) behind a delegating encoder so the algorithm can be upgraded;
  at least 8 characters with a letter and a digit. Never logged or returned.
- **Account protection**: 5 failed sign-ins lock the account for 15 minutes; sign-in requires a
  verified e-mail (`REQUIRE_EMAIL_VERIFICATION`, forced on in `prod`); disabled accounts cannot
  sign in and are signed out. Password reset and "resend verification" answer identically whether
  or not the account exists.
- **Access tokens**: HS256 JWTs signed with `JWT_SECRET` (32+ characters, from the environment),
  15-minute lifetime, validated by Spring Security's resource server. The frontend keeps them in
  memory only, never in `localStorage`.
- **Refresh tokens**: random opaque values stored only as SHA-256 hashes, sent as an `HttpOnly`,
  `Secure` (in production), `SameSite=Strict` cookie scoped to `/api/auth`. Every refresh rotates
  the token; presenting an already-rotated token outside a 30-second grace window revokes the
  whole token family (theft detection). Sign-out revokes the family; changing the password
  revokes all sessions.
- **E-mail tokens** (verification 24 h, password reset 30 min) are single-use and stored hashed.

## Authorization

- Stateless Spring Security with explicit rules: public catalog reads, authenticated `/api/**`,
  `ADMIN` for `/api/admin/**` (enforced in the URL rules and again with `@PreAuthorize` on
  every admin controller). The frontend's admin guard is only cosmetic.
- Ownership is checked in queries: customers can only read or act on their own orders, addresses,
  reviews, carts and payments; other ids behave as "not found".
- WebSocket: the `CONNECT` frame must carry a valid access token; each `SUBSCRIBE` is authorised
  (own order topics and user queues only; admin topics need the admin role).
- The AI service's data endpoints (`/internal/**`) sit in a separate filter chain that only
  accepts the shared `X-Internal-Api-Key` and are not routed by the public proxy.

## Payments

- The server calculates every amount (prices, discounts, coupons, GST, shipping) inside the
  checkout transaction and creates the Razorpay order for that total. Client-supplied prices and
  payment states are never trusted.
- An order becomes paid only after the Checkout signature (HMAC-SHA256 with the key secret)
  verifies **and** the payment fetched from Razorpay's API matches the order and amount.
- Webhooks are verified with `RAZORPAY_WEBHOOK_SECRET` over the raw body and de-duplicated by
  event id. A reconciliation job settles lost callbacks; payments captured for cancelled orders
  are refunded automatically.
- The Razorpay key secret and webhook secret exist only in the backend environment. The browser
  receives only the public key id.

## Web protections

- **CSRF**: API calls authenticate with a bearer token, which browsers do not attach
  automatically. The only cookie-authenticated endpoints (`/api/auth/refresh`, `/logout`)
  require `X-Requested-With`, which cross-site forms cannot send, and the cookie is
  `SameSite=Strict`.
- **CORS**: only origins in `CORS_ALLOWED_ORIGINS`; in the Docker deployment the SPA and API
  share one origin.
- **Headers**: the backend sends a restrictive CSP, `X-Frame-Options: DENY`,
  `X-Content-Type-Options: nosniff` and HSTS. Nginx adds a CSP for the SPA that allows only
  Razorpay Checkout as a third-party script/frame, plus `Referrer-Policy` and
  `Permissions-Policy`.
- **Input validation**: Bean Validation on every request DTO (lengths, patterns, ranges),
  mirrored in the frontend. JPA with bound parameters everywhere (no string-built SQL). React
  escapes output; the app never renders user HTML.
- **Uploads**: images are decoded to verify their real format (JPEG/PNG only; the declared type
  and file name are ignored), size- and dimension-limited (5 MB, 8000 px, 40 MP, decompression
  bombs rejected before decoding), re-encoded, and stored under random names outside the web
  root. External image links must be `https`.
- **Rate limiting** per client IP: 10/min on credential endpoints, 300/min on the rest of the
  API (HTTP 429 with `Retry-After`).
- **Errors** never include stack traces or internal messages; responses carry safe messages and
  error codes.

## Secrets and configuration

- All secrets come from environment variables (`.env` locally, a secret manager in production);
  `.env` is git-ignored and `.env.example` contains placeholders only.
- The application refuses to start without `JWT_SECRET` (minimum length enforced).
- CI uses no secrets.

## Logging and audit

- Logs never contain passwords, tokens, Razorpay secrets or signatures, or card/UPI details (the
  app never sees them; Razorpay collects them).
- The audit log records administrative actions (products and prices, categories, brands, stock,
  coupons, order status and cancellations, COD collection, review moderation, settings, customer
  status) and security events (registrations, password changes and resets, account lockouts),
  with actor and IP.

## Operator checklist

- [ ] `SPRING_PROFILES_ACTIVE=prod`, HTTPS in front of Nginx, `COOKIE_SECURE=true`.
- [ ] Strong unique values for `JWT_SECRET`, `DATABASE_PASSWORD`, `MYSQL_ROOT_PASSWORD`,
      `AI_SERVICE_API_KEY`, `RAZORPAY_WEBHOOK_SECRET`.
- [ ] `/actuator` and `/internal` not reachable from the internet; MySQL not published.
- [ ] Database backups encrypted and restore-tested.
- [ ] Dependencies kept current (Dependabot or `mvn versions:display-dependency-updates`,
      `npm outdated`, `pip list --outdated`).

## Known limitations

- Rate limiting is per instance and per IP; behind a shared NAT many users share a budget.
  Add edge rate limiting (CDN/WAF) for production traffic.
- No multi-factor authentication for administrators yet.

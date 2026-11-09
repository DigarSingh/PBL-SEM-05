# Deployment

## Profiles

| Profile | Use | Behaviour |
| --- | --- | --- |
| `dev` (default) | local development, demos | reads `../.env`, seeds demo data when the catalog is empty, Swagger UI on, debug logging |
| `prod` | real deployments | no seeding, API docs off, `Secure` cookies and e-mail verification forced on, JSON (ECS) logs |
| `test` | automated tests | Testcontainers or `TEST_DATABASE_URL` |

Select with `SPRING_PROFILES_ACTIVE`.

## Required configuration

| Variable | Notes |
| --- | --- |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | MySQL 8, `utf8mb4`; keep `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` in the URL |
| `JWT_SECRET` | 32+ random characters; rotating it signs everyone out |
| `AI_SERVICE_URL`, `AI_SERVICE_API_KEY` | same key on backend and AI service |
| `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS` | the public https origin of the store |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_STARTTLS`, `MAIL_FROM` | a real SMTP provider in production |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | live keys only in production |
| `UPLOAD_DIR` | persistent volume for product images |

Keep secrets in the platform's secret manager (or a root-owned `.env` on a single host), never in
the image or the repository.

## Docker Compose

`docker-compose.yml` runs MySQL, Mailpit, the backend, the AI service and the Nginx frontend with
health checks and start-up ordering. For a production-like run on one host:

1. Set in `.env`: strong secrets, `SPRING_PROFILES_ACTIVE=prod`, `SEED_ENABLED=false`,
   `COOKIE_SECURE=true`, `PUBLIC_URL=https://shop.example.com`, and real `MAIL_*` values.
2. Set `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`,
   `MAIL_STARTTLS` and `MAIL_FROM` to your SMTP provider's values. Compose passes these settings
   through to the backend; Mailpit remains the default for local development. Remove the published
   MySQL/Mailpit/backend ports in production.
3. Put TLS in front of the frontend container (a load balancer, Caddy or an Nginx/Traefik edge).
   The frontend container listens on port 8080 inside the compose network.
4. `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build`

The Nginx configuration (`frontend/nginx/`) serves the SPA, proxies `/api`, `/ws` (WebSocket
upgrade) and `/uploads` to the backend, returns 404 for `/internal` and `/actuator`, sets a
Content-Security-Policy that allows Razorpay Checkout, and caches hashed assets for a year.

## Database

- Flyway applies migrations from `backend/src/main/resources/db/migration` on start-up; Hibernate
  only validates the schema (`ddl-auto: validate`).
- Back up with `mysqldump --single-transaction --routines shopsmart` (or managed snapshots) and
  test restores. Add new migrations as `V2__...sql`; never edit an applied migration.
- The application user needs DML and DDL rights on its schema only.

## Razorpay go-live checklist

1. Switch to **live** keys; the key secret goes only to the backend environment.
2. Create a webhook in the Razorpay dashboard pointing to
   `https://<your-domain>/api/payments/razorpay/webhook` with a strong secret (set as
   `RAZORPAY_WEBHOOK_SECRET`) and the events `payment.authorized`, `payment.captured`,
   `payment.failed`, `order.paid`, `refund.processed`, `refund.failed`.
3. For local webhook testing, expose the backend with a tunnel (for example
   `cloudflared tunnel --url http://localhost:8080` or `ngrok http 8080`) and use that URL in a
   test-mode webhook.
4. Place a small real order, cancel it, and confirm the refund reaches the payment method.
5. Keep `app.jobs.enabled=true` so the reconciliation job settles payments whose browser callback
   or webhook was lost.

## Monitoring

- Health: `/actuator/health/liveness` and `/actuator/health/readiness` (public; the compose
  health checks use them). Everything else under `/actuator` (including `prometheus` and
  `metrics`) requires an admin token; do not expose `/actuator` through the public proxy.
- AI service: `GET /health` reports the embedding backend, catalog size and last refresh error.
- Logs: JSON (ECS) in `prod`; secrets, tokens and passwords are never logged. The audit log
  (*Admin > Settings*) records administrative and security-relevant actions.

## Scaling

The backend is designed to run as **one instance** today:
- STOMP uses Spring's in-memory simple broker: switch to a broker relay (RabbitMQ or ActiveMQ with
  the STOMP plugin) so pushes reach clients connected to any instance.
- Rate-limit buckets are in memory: move them to Redis (Bucket4j supports it) or rate limit at
  the edge.
- `UPLOAD_DIR` must become shared storage (or implement `StorageService` for S3-compatible
  storage).

Scheduled jobs, checkout locking and webhook handling are already safe with several instances.
The AI service is stateless and can be replicated freely.

## CI

`.github/workflows/ci.yml` runs on every push to `main` and on pull requests: backend
`mvn verify` (Testcontainers), AI service `ruff` + `pytest`, frontend lint, typecheck, tests and
build, then builds all three Docker images and validates the compose file. It uses no secrets and
does not deploy; add a deploy job gated on this workflow (and on protected environments) when a
target platform is chosen.

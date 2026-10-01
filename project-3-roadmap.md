# Project 3: EventHub — Ticket Booking & Order Processing System

**Why this shape:** Booking systems give you a *natural* reason to need optimistic locking (seat race conditions), caching (hot event/seat-availability reads), and event-driven flow (booking → payment → notification) all in one coherent domain — instead of bolting three unrelated demos together.

**Pace assumption:** ~1–2 hrs/day, 5 days/week → 24 working days (~5 weeks). If you're full-time, compress each day into half a day.

**Golden rule for the whole build:** commit at the end of every day, even if the feature isn't "done." Small incremental commits are the whole point — that's what separates this from a portfolio project that looks like it was uploaded in one shot.

---

## Week 1 — Foundation & Domain

### Day 1 — Project skeleton
- Init repo, Spring Boot project (Web, Data JPA, Validation, Actuator, Postgres driver)
- `docker-compose.yml` with Postgres only (app runs locally against it for now)
- Basic `/actuator/health` working end to end
- Flyway added, empty baseline migration
- **Commit:** `chore: project scaffold + postgres via docker-compose`

### Day 2 — Domain model
- Entities: `Venue`, `Event`, `Seat`, `User`, `Booking`, `BookingItem`
- Relationships: Venue 1→N Event, Event 1→N Seat, User 1→N Booking, Booking 1→N BookingItem→Seat
- Flyway migration for schema (no Hibernate auto-ddl in this project — set the tone early)
- **Commit:** `feat: core domain entities + initial schema migration`

### Day 3 — Layering: Venue & Event
- DTOs (never expose entities), mappers (MapStruct or manual)
- Controller → Service → Repository for Venue and Event (CRUD)
- **Commit:** `feat: venue and event CRUD with DTO layer`

### Day 4 — Listing & discovery
- Paginated, sortable `GET /events` (upcoming events, filter by venue/date)
- `@Query`/Specifications for dynamic filtering (by city, date range, category)
- **Commit:** `feat: paginated event search with dynamic filtering`

### Day 5 — Validation & error handling
- Bean validation on request DTOs, a couple of custom validators (e.g. event date must be future)
- `@ControllerAdvice` global exception handler with a consistent error response shape
- First batch of unit tests (service layer, Mockito)
- **Commit:** `feat: validation + global exception handling, initial unit tests`

---

## Week 2 — Concurrency & Caching

### Day 6 — Booking creation flow
- `POST /bookings`: pick seats for an event, create Booking + BookingItems, mark seats RESERVED
- Add `@Version` to `Seat` for optimistic locking
- **Commit:** `feat: booking creation flow with optimistic locking on seats`

### Day 7 — Prove the concurrency handling
- Integration test that fires concurrent booking requests at the same seat (Testcontainers Postgres, multi-threaded test or parallel requests)
- Handle `OptimisticLockException` → clean 409 Conflict response, not a stack trace
- **Commit:** `test: concurrent booking race condition + conflict handling`

### Day 8 — Redis integration
- Add Redis to docker-compose, Spring Cache config
- Cache-aside on `GET /events` and `GET /events/{id}/seats` (read-heavy, natural cache candidates)
- **Commit:** `feat: redis cache-aside for event and seat-availability reads`

### Day 9 — Cache correctness
- Evict/update cache on booking (seat availability must never serve stale data after a booking)
- TTL tuning, cache key strategy documented in code comments
- **Commit:** `fix: cache invalidation on seat state change`

### Day 10 — Cache tests
- Testcontainers Redis integration test: verify cache hit/miss behavior and invalidation
- Buffer time to fix anything Week 2 left rough
- **Commit:** `test: redis cache invalidation coverage`

---

## Week 3 — Event-Driven Architecture

### Day 11 — Broker setup
- Add RabbitMQ (simplest for a portfolio demo — Kafka is heavier to justify unless you want the extra flex) to docker-compose
- Producer config, exchange/queue topology defined in code
- **Commit:** `chore: rabbitmq setup + topology config`

### Day 12 — Publish domain events
- On successful booking → publish `BookingConfirmedEvent`
- Keep the publish inside the same transaction boundary discussion in mind (outbox pattern is a stretch goal, mention as a trade-off in README if skipped)
- **Commit:** `feat: publish booking-confirmed event on successful booking`

### Day 13 — Consumer: notifications
- Separate `NotificationListener` consumes `BookingConfirmedEvent`, logs/mocks an email send
- This is the "decoupling" proof point — booking flow doesn't know or care who listens
- **Commit:** `feat: notification consumer for booking-confirmed events`

### Day 14 — Payment step
- Add a mocked `PaymentService` → `PaymentProcessedEvent` → booking status update (PENDING → CONFIRMED)
- Small state machine on Booking status now: PENDING, CONFIRMED, CANCELLED, FAILED
- **Commit:** `feat: mock payment step with booking status state machine`

### Day 15 — Resilience on the consumer side
- Retry policy + dead-letter queue for failed consumers
- Integration tests for the full event chain (booking → payment event → notification event)
- **Commit:** `feat: retry + DLQ for event consumers, end-to-end event flow tests`

---

## Week 4 — Production Readiness

### Day 16 — Security & ownership
- Minimal JWT auth (reuse patterns from Project 2, don't rebuild from scratch) — users can only view/cancel their own bookings
- Actuator `/health`, `/info`, `/metrics` exposed properly (not wide open — lock down in application.yml)
- **Commit:** `feat: JWT auth + booking ownership checks, actuator endpoints`

### Day 17 — Structured logging
- JSON structured logs (Logback encoder), correlation/request ID per request (filter + MDC)
- Log key lifecycle events: booking created, payment processed, notification sent
- **Commit:** `feat: structured JSON logging with correlation IDs`

### Day 18 — Full dockerization
- Multi-stage `Dockerfile` for the app (small final image)
- Finalize `docker-compose.yml`: app + postgres + redis + rabbitmq, one `docker compose up` should run everything
- **Commit:** `chore: multi-stage dockerfile + full docker-compose stack`

### Day 19 — CI pipeline
- GitHub Actions: run tests (with Testcontainers) + build on every push, badge in README
- **Commit:** `ci: github actions test + build pipeline`

### Day 20 — CD (optional but a nice differentiator)
- Extend the workflow to build and push a Docker image to GitHub Container Registry on merge to main
- **Commit:** `ci: build and push docker image on main`

### Day 21 — Stress-test what you built
- Quick load test (k6 or a simple script) hitting the booking endpoint concurrently against the real stack — confirm optimistic locking + caching hold up under load, not just in unit tests
- Fix whatever breaks
- **Commit:** `fix: issues found under concurrent load testing`

### Day 22 — Architecture diagram + design decisions
- Draw.io export: request flow, event flow, cache layer, docker network
- README "Design decisions & trade-offs" section — explicitly call out things like "chose RabbitMQ over Kafka because X," "skipped outbox pattern because Y, here's the risk"
- **Commit:** `docs: architecture diagram + design decisions section`

### Day 23 — Docs polish
- OpenAPI/Swagger cleanup, seed data script (`docker exec` or Flyway seed migration) so a recruiter can run it and see real data immediately
- **Commit:** `docs: swagger polish + demo seed data`

### Day 24 — Final pass
- Read the README as if you're a recruiter with 30 seconds — trim anything that doesn't earn its place
- Clean up any messy commit history you can still squash safely, tag `v1.0`
- **Commit:** `chore: v1.0 release polish`

---

## Notes on sequencing
- Concurrency (Week 2) comes **before** events (Week 3) on purpose — the optimistic locking story is the single most "interview-relevant" piece here, so it should be solid and tested before you layer more complexity on top.
- Security is deliberately late (Day 16) — you'll likely reuse most of it from Project 2, so this project shouldn't spend real time reinventing it.
- If you're short on time, Day 20 (CD/image push) and the load test on Day 21 are the safest days to cut — everything else directly demonstrates one of the "what this proves" bullets from your original plan.

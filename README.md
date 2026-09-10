# EventHub — Booking & Order Processing System

A production-grade event/ticket booking system demonstrating optimistic locking under concurrency, Redis caching, and event-driven order processing in Spring Boot. This is Project 3 of a 3-project backend portfolio (Core REST API → Auth & Authorization → **Production-grade Booking/Order System**).

**Status:** 🚧 Day 2 — domain model & schema. Business logic, endpoints, concurrency handling, caching, and the event-driven pipeline land over the following days (see [Roadmap](#roadmap) below).

---

## Tech stack

| Concern            | Choice                                   |
|---------------------|-------------------------------------------|
| Language / runtime   | Java 21                                   |
| Framework            | Spring Boot 3.3.4 (Web, Data JPA, Validation, Actuator) |
| Database              | PostgreSQL 16                              |
| Migrations             | Flyway                                     |
| Caching                | Redis (from Day 8)                         |
| Messaging               | RabbitMQ (from Day 11)                     |
| Testing                  | JUnit 5, Mockito, Testcontainers            |
| Build                     | Maven                                       |
| Containerization           | Docker / Docker Compose                     |
| CI                          | GitHub Actions (from Day 19)                |

## What Day 1 sets up

- Spring Boot application skeleton with a clean package structure
- PostgreSQL running via Docker Compose **on host port `5433`** (not the Postgres default `5432`), so it won't collide with a local Postgres instance already running on your machine
- Flyway wired in with a baseline migration, proving the app → Flyway → Postgres pipeline end to end before any domain modeling starts
- Actuator health check exposed at `/actuator/health`
- A Testcontainers-backed smoke test that boots the full Spring context against a real Postgres container

No domain entities, business logic, or endpoints beyond health checks exist yet — that's intentional. See the Roadmap for what lands each day.

## What Day 2 adds

- Six JPA entities modeling the actual booking domain: `User`, `Venue`, `Event`, `Seat`, `Booking`, `BookingItem`
- Real relationships, not a flat schema: `Venue 1—N Event 1—N Seat`, `User 1—N Booking 1—N BookingItem N—1 Seat`
- A `V2__domain_schema.sql` migration that matches the entities exactly, so `ddl-auto: validate` (see `application.yml`) fails loudly on any mismatch instead of Hibernate silently patching the schema
- Still no controllers, services, or repositories — those start Day 3. The existing Testcontainers smoke test now also doubles as a schema/entity consistency check, since the context won't start if they disagree.

## Prerequisites

- Java 21 (JDK)
- Maven 3.9+
- Docker + Docker Compose

## Running locally

1. Start Postgres:
   ```bash
   docker compose up -d
   ```
2. Run the app:
   ```bash
   mvn spring-boot:run
   ```
3. Confirm it's healthy:
   ```bash
   curl http://localhost:8080/actuator/health
   ```
   Expected: `{"status":"UP"}`

## Running the tests

```bash
mvn test
```

The test suite uses Testcontainers, so Docker must be running — it will spin up (and tear down) its own disposable Postgres container, independent of the one from `docker compose up`.

## Configuration

All datasource settings are overridable via environment variables, with sane local defaults baked in:

| Variable       | Default          | Notes                                   |
|-----------------|-------------------|------------------------------------------|
| `DB_PORT`         | `5433`             | Matches the host port in `docker-compose.yml` |
| `DB_NAME`           | `eventhub_db`        |                                            |
| `DB_USER`             | `eventhub_user`        |                                            |
| `DB_PASSWORD`           | `eventhub_pass`          | Local dev only — never used as-is in a real deployment |
| `SERVER_PORT`             | `8080`                    |                                            |

## Project structure

```
src/main/java/com/ahdyahmed/eventhub/
├── EventhubApplication.java   # entry point
├── common/
│   └── BaseEntity.java        # shared id + audit columns, JPA-safe equals/hashCode
├── user/
│   └── User.java
├── venue/
│   └── Venue.java
├── event/
│   └── Event.java
├── seat/
│   ├── Seat.java
│   └── SeatStatus.java
├── booking/
│   ├── Booking.java
│   ├── BookingItem.java
│   └── BookingStatus.java
└── config/                    # cross-cutting configuration (empty for now)

src/main/resources/
├── application.yml
└── db/migration/
    ├── V1__baseline.sql
    └── V2__domain_schema.sql  # users, venues, events, seats, bookings, booking_items

src/test/java/com/ahdyahmed/eventhub/
└── EventhubApplicationTests.java   # Testcontainers context + schema/entity consistency check
```

Packages are organized **by feature (vertical slice)**, not by technical layer (i.e. no top-level `entity/`, `repository/`, `service/`, `controller/` packages holding everything). Each domain concept — `user`, `venue`, `event`, `seat`, `booking` — owns its own entity, and will own its own repository/service/controller/DTOs as those land in the coming days. This scales better than layer-first packaging once a domain has more than a handful of types, and it's the structure the rest of the project follows from here on.

## Roadmap

**Week 1 — Foundation & domain**
- [x] Day 1 — project scaffold, Postgres via Docker Compose, Flyway baseline, health check
- [x] Day 2 — domain entities (Venue, Event, Seat, User, Booking, BookingItem) + schema migration
- [ ] Day 3 — layered CRUD (Controller → Service → Repository → DTO) for Venue & Event
- [ ] Day 4 — paginated, sortable, dynamically filterable event search
- [ ] Day 5 — bean validation, global exception handling, first unit tests

**Week 2 — Concurrency & caching**
- [ ] Day 6 — booking creation flow with `@Version` optimistic locking on seats
- [ ] Day 7 — concurrency test proving the race condition is handled correctly
- [ ] Day 8 — Redis cache-aside on read-heavy event/seat endpoints
- [ ] Day 9 — cache invalidation on booking/seat state change
- [ ] Day 10 — Testcontainers Redis test coverage

**Week 3 — Event-driven architecture**
- [ ] Day 11 — RabbitMQ setup + topology
- [ ] Day 12 — publish `BookingConfirmedEvent`
- [ ] Day 13 — notification consumer
- [ ] Day 14 — mock payment step + booking status state machine
- [ ] Day 15 — retry/DLQ for consumers + end-to-end event flow tests

**Week 4 — Production readiness**
- [ ] Day 16 — JWT auth + booking ownership checks, Actuator hardening
- [ ] Day 17 — structured JSON logging with correlation IDs
- [ ] Day 18 — multi-stage Dockerfile + full docker-compose stack (app + Postgres + Redis + RabbitMQ)
- [ ] Day 19 — GitHub Actions CI (test + build on push)
- [ ] Day 20 — GitHub Actions CD (build & push image)
- [ ] Day 21 — load test under concurrency, fix findings
- [ ] Day 22 — architecture diagram + design decisions section
- [ ] Day 23 — OpenAPI/Swagger polish + demo seed data
- [ ] Day 24 — final polish, `v1.0` tag

## Design decisions

- **Postgres on host port 5433, not 5432** — avoids clashing with a locally running Postgres instance. The app's own default (`DB_PORT=5433`) is kept in sync with `docker-compose.yml` so nothing needs to be edited to run this out of the box.
- **`ddl-auto: validate`, not Hibernate auto-DDL** — the schema is owned entirely by Flyway migrations from day one. This is a deliberate habit: letting Hibernate generate schema is fine for a toy project, but it's not how you'd run this in a team, and this repo is meant to read as production-adjacent throughout, not just at the end.
- **Testcontainers over an in-memory database (e.g. H2) for tests** — tests run against the same database engine as production. This matters more here than in most projects because Day 7's whole point is proving optimistic-locking behavior under real concurrency, which an in-memory substitute wouldn't faithfully represent.
- **Feature packages, not layer packages** — `user/`, `venue/`, `event/`, `seat/`, `booking/` instead of a flat `entity/` + `repository/` + `service/`. Keeps everything related to one domain concept in one place as the project grows past Day 2's entity-only state.
- **No `@Version` on `Seat` yet, even though optimistic locking is the whole point of this project** — it's added on Day 6 next to the booking flow that actually exercises it, on purpose. Adding it speculatively on Day 2 would mean the commit that's supposed to demonstrate concurrency control shows up with nothing to actually show.
- **`equals`/`hashCode` based only on a non-null `id`, not Lombok's field-based default** — field-based equality breaks on Hibernate proxies and recurses infinitely across bidirectional associations (`Booking` ↔ `BookingItem`, etc.). `BaseEntity` implements the standard safe pattern once, and every entity inherits it.
- **`price_at_booking` captured on `BookingItem` instead of read live from `Seat`** — a booking's price shouldn't silently change if the seat's price is edited later. Small detail, but it's the kind of thing that matters in a real order-processing system and costs nothing to get right now.

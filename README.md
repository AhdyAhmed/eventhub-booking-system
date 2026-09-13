# EventHub — Booking & Order Processing System

A production-grade event/ticket booking system demonstrating optimistic locking under concurrency, Redis caching, and event-driven order processing in Spring Boot. This is Project 3 of a 3-project backend portfolio (Core REST API → Auth & Authorization → **Production-grade Booking/Order System**).

**Status:** 🚧 Day 6 — booking flow with optimistic locking. Caching, the event-driven pipeline, auth, and production hardening land over the following days (see [Roadmap](#roadmap) below).

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

## What Day 3 adds

Full `Controller → Service → Repository` layering for **Venue** and **Event**, with DTOs at the boundary — no JPA entity is ever serialized directly in a response or bound directly from a request body.

- `VenueRepository`, `EventRepository` — plain `JpaRepository` at this point; `EventRepository` gains `JpaSpecificationExecutor` on Day 4 for dynamic filtering
- `VenueService`/`VenueServiceImpl`, `EventService`/`EventServiceImpl` — interface + implementation, constructor-injected, `@Transactional` boundaries set correctly (`readOnly = true` on reads)
- `VenueMapper`, `EventMapper` — small hand-written mapping classes; `EventMapper` maps `Venue` down to a local `VenueSummary` rather than reusing `venue.dto.VenueResponse`, so the `event` package doesn't depend on `venue`'s response shape
- `VenueController`, `EventController` — standard REST verbs under `/api/v1/venues` and `/api/v1/events`
- `ResourceNotFoundException` — a lightweight `@ResponseStatus(404)` exception used when an id lookup misses. This is a deliberate stopgap: Day 5 replaces it with a proper `@ControllerAdvice` giving every exception type a consistent JSON error shape

**API surface added:**

| Method | Path                   | Purpose               |
|--------|------------------------|------------------------|
| POST   | `/api/v1/venues`        | Create a venue          |
| GET    | `/api/v1/venues`         | List all venues          |
| GET    | `/api/v1/venues/{id}`     | Get one venue              |
| PUT    | `/api/v1/venues/{id}`      | Update a venue               |
| DELETE | `/api/v1/venues/{id}`       | Delete a venue                |
| POST   | `/api/v1/events`             | Create an event (by `venueId`) |
| GET    | `/api/v1/events`               | Paginated, filterable, sortable event search — see below |
| GET    | `/api/v1/events/{id}`            | Get one event                       |
| PUT    | `/api/v1/events/{id}`             | Update an event                      |
| DELETE | `/api/v1/events/{id}`              | Delete an event                       |
| POST   | `/api/v1/users`                      | Create a user (stand-in until Day 16's real auth) |
| GET    | `/api/v1/users/{id}`                   | Get one user                            |
| POST   | `/api/v1/events/{eventId}/seats`         | Add a seat to an event                    |
| GET    | `/api/v1/events/{eventId}/seats`           | List an event's seats, optional `?status=` filter |
| POST   | `/api/v1/bookings`                           | Create a booking — reserves one or more seats |
| GET    | `/api/v1/bookings/{id}`                        | Get one booking                                |

## What Day 4 adds

`GET /api/v1/events` is no longer "return every row" — it's a proper paginated, sortable, dynamically filterable search endpoint, without turning into a wall of `if` statements.

- **Pagination & sorting** — standard Spring Data `Pageable` binding: `?page=0&size=20&sort=eventDate,asc`. Sorting is repeatable (`&sort=category,asc`) and works on nested properties like `venue.city` too, since it's resolved via the JPA Criteria path, not a hand-written query.
- **Dynamic filtering via `Specification`** — `EventSpecifications` has one small, independently testable specification per filterable field (`venueId`, `city`, `category`, `fromDate`/`toDate`). `EventServiceImpl` chains them with `Specification.where(...).and(...)`, and Spring Data treats a `null` specification as a no-op — so all five filters can be chained unconditionally and only the ones the caller actually supplied end up narrowing the query.
- **`PageResponse<T>`** — a small wrapper in `common/dto` so Spring Data's `Page<T>` (and its version-coupled, fairly verbose JSON shape) never gets serialized directly in a response. Same principle Day 3 applied to entities, extended to pagination metadata.
- **Venue listing is untouched** — still a plain `GET /api/v1/venues` with no pagination. Venues are low-cardinality reference data in this domain; Events are the resource that actually needs filtering, so that's where the Day 4 effort goes rather than adding pagination everywhere on principle.

**Try the search endpoint:**

```bash
# All upcoming events, 20 per page, soonest first (the defaults)
curl "http://localhost:8080/api/v1/events"

# Page 2, 5 per page
curl "http://localhost:8080/api/v1/events?page=1&size=5"

# Filter by city + category, sorted by date descending
curl "http://localhost:8080/api/v1/events?city=Cairo&category=CONCERT&sort=eventDate,desc"

# Date range filter
curl "http://localhost:8080/api/v1/events?fromDate=2026-01-01T00:00:00Z&toDate=2026-12-31T23:59:59Z"
```

## What Day 5 adds

Requests are now actually validated, and every exception in the app funnels through one consistent error shape instead of Spring's default error page or a scatter of `@ResponseStatus` annotations.

- **Bean validation on `VenueRequest` and `EventRequest`** — standard constraints (`@NotBlank`, `@NotNull`, `@Positive`, `@Size`) plus `@Valid` on every `POST`/`PUT` controller method
- **Two custom validators**, because the interesting validation rules here aren't ones the built-in constraints cover:
  - `@FutureByHours(hours = 1)` — an event's `eventDate` must be at least N hours out, not merely "in the future." Deliberately generic (lives in `common/validation`) so any future "needs lead time" rule can reuse it.
  - `@ValidCategory` — restricts `Event.category` to a fixed set (`CONCERT`, `SPORTS`, `THEATER`, `CONFERENCE`, `EXHIBITION`, `OTHER`), case-insensitively. Lives in `event/validation` since it's specific to the Event domain.
- **`GlobalExceptionHandler`** (`@RestControllerAdvice`) — the actual replacement for Day 3's `@ResponseStatus` stopgap. Handles validation failures, `ResourceNotFoundException`, malformed JSON, and a catch-all for anything unexpected (logged server-side, never exposed to the client — leaking stack traces in an API response is an information-disclosure risk, not just an ugly response).
- **`ErrorResponse`** — one shape for every error the API returns, with an optional `fieldErrors` map that's only populated for validation failures.
- **First unit tests**: `VenueServiceImplTest` and `EventServiceImplTest` (JUnit 5 + Mockito — only the repository is mocked, mappers run for real since they have no side effects to fake), plus `EventRequestValidationTest`, which exercises both custom validators directly through a real `jakarta.validation.Validator` with no Spring context needed.

**What a validation failure looks like now:**

```bash
curl -i -X POST http://localhost:8080/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{"venueId":1,"name":"","category":"NOT_REAL","eventDate":"2020-01-01T00:00:00Z"}'
```

```json
{
  "timestamp": "2026-09-13T10:15:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/events",
  "fieldErrors": {
    "name": "name is required",
    "category": "must be one of: CONCERT, SPORTS, THEATER, CONFERENCE, EXHIBITION, OTHER",
    "eventDate": "eventDate must be at least 1 hour from now"
  }
}
```

**A 404 now looks like:**

```json
{
  "timestamp": "2026-09-13T10:16:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Event not found with id 999",
  "path": "/api/v1/events/999",
  "fieldErrors": null
}
```

## What Day 6 adds

The actual thesis of this project: a booking flow that survives two people trying to grab the same seat at the same time, plus the minimum scaffolding the booking flow needs to have anything to book.

**Prerequisite scaffolding (not a separate roadmap day, bundled in here since the booking flow is the first thing that needs real data in these tables):**
- Minimal **User** create/get (`UserController`/`UserService`) — no password, no roles, no update/delete. This is a stand-in until Day 16's real auth module owns registration; expect it to be revisited then.
- Minimal **Seat** create/list-by-event (`SeatController`/`SeatService`), nested under `/api/v1/events/{eventId}/seats`.

**The actual feature:**
- **`@Version` added to `Seat`** (`V3__seat_optimistic_locking.sql`) — every `UPDATE` Hibernate issues against a seat now includes `AND version = ?`, and bumps it on success. A concurrent update that already changed the row makes the next writer's update match zero rows.
- **`POST /api/v1/bookings`** — takes a `userId` and a list of `seatIds`, and either reserves every seat and creates the booking, or reserves none of them. Booking status starts at `PENDING`; seats move `AVAILABLE → RESERVED` (not `BOOKED` yet — that transition is Day 14's payment step).
- **Two-layer concurrency defense** in `BookingServiceImpl.reserve()`:
  1. A cheap pre-check: if `seat.getStatus() != AVAILABLE`, reject immediately.
  2. The real guard: each seat update is saved with `saveAndFlush()` immediately (not batched), so a version conflict is caught and attributed to the exact seat that lost the race, not deferred to one big flush at the end where that information is lost.
  
  Both paths converge on the same `SeatUnavailableException` (409) — from the client's side, "the seat wasn't available" is one outcome regardless of which layer caught it. **Day 7 is the test that actually fires concurrent requests and proves layer 2 holds.**
- **All-or-nothing atomicity** — if any seat in a multi-seat booking fails (pre-check or version conflict), the exception propagates out of the `@Transactional` method and Spring rolls back the *entire* transaction, including seats that were already successfully reserved earlier in the same loop. A booking never partially succeeds.
- **Two new exceptions**: `SeatUnavailableException` (409) and `BookingValidationException` (400, for cross-seat rules like "all seats must belong to the same event" that a per-field bean validation annotation can't express) — both wired into `GlobalExceptionHandler`, along with a defensive fallback handler for any raw `ObjectOptimisticLockingFailureException` that might escape from a future write path that isn't as careful.

**Try the full flow:**

```bash
# 1. A venue, an event, a seat, and a user to book with
curl -X POST http://localhost:8080/api/v1/venues -H "Content-Type: application/json" \
  -d '{"name":"Cairo Arena","city":"Cairo","address":"Nasr City","capacity":5000}'

curl -X POST http://localhost:8080/api/v1/events -H "Content-Type: application/json" \
  -d '{"venueId":1,"name":"Launch Night","description":"Opening event","category":"CONCERT","eventDate":"2026-12-01T19:00:00Z"}'

curl -X POST http://localhost:8080/api/v1/events/1/seats -H "Content-Type: application/json" \
  -d '{"seatNumber":"A1","section":"Floor","price":50.00}'

curl -X POST http://localhost:8080/api/v1/users -H "Content-Type: application/json" \
  -d '{"fullName":"Ahmed Test","email":"ahmed@example.com"}'

# 2. Book it
curl -X POST http://localhost:8080/api/v1/bookings -H "Content-Type: application/json" \
  -d '{"userId":1,"seatIds":[1]}'

# 3. Try to book the same seat again — this is what a 409 looks like today
#    (Day 7 proves this same outcome holds even when both requests race)
curl -i -X POST http://localhost:8080/api/v1/bookings -H "Content-Type: application/json" \
  -d '{"userId":1,"seatIds":[1]}'
```

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

Most of the suite (`VenueServiceImplTest`, `EventServiceImplTest`, `EventRequestValidationTest`) is plain JUnit 5 + Mockito and needs nothing beyond the JVM. `EventhubApplicationTests` is the exception — it uses Testcontainers to boot the full Spring context against a real, disposable Postgres, so Docker must be running for the full suite to pass.

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
│   ├── BaseEntity.java             # shared id + audit columns, JPA-safe equals/hashCode
│   ├── dto/
│   │   └── PageResponse.java       # framework-agnostic pagination wrapper
│   ├── exception/
│   │   ├── ResourceNotFoundException.java
│   │   ├── SeatUnavailableException.java   # 409 — business-state or optimistic-lock conflict
│   │   ├── BookingValidationException.java # 400 — cross-field booking rules
│   │   ├── ErrorResponse.java      # one error shape for the whole API
│   │   └── GlobalExceptionHandler.java
│   └── validation/
│       ├── FutureByHours.java      # custom constraint: "at least N hours from now"
│       └── FutureByHoursValidator.java
├── user/
│   ├── User.java
│   ├── UserRepository.java
│   ├── UserService.java
│   ├── UserServiceImpl.java
│   ├── UserController.java
│   ├── UserMapper.java
│   └── dto/
│       ├── UserRequest.java
│       └── UserResponse.java
├── venue/
│   ├── Venue.java
│   ├── VenueRepository.java
│   ├── VenueService.java
│   ├── VenueServiceImpl.java
│   ├── VenueController.java
│   ├── VenueMapper.java
│   └── dto/
│       ├── VenueRequest.java
│       └── VenueResponse.java
├── event/
│   ├── Event.java
│   ├── EventRepository.java
│   ├── EventService.java
│   ├── EventServiceImpl.java
│   ├── EventController.java
│   ├── EventMapper.java
│   ├── EventSpecifications.java    # one Specification per filterable field
│   ├── validation/
│   │   ├── ValidCategory.java      # custom constraint: fixed category allow-list
│   │   └── ValidCategoryValidator.java
│   └── dto/
│       ├── EventRequest.java
│       ├── EventResponse.java
│       ├── EventSearchCriteria.java
│       └── VenueSummary.java
├── seat/
│   ├── Seat.java                   # now carries @Version
│   ├── SeatStatus.java
│   ├── SeatRepository.java
│   ├── SeatService.java
│   ├── SeatServiceImpl.java
│   ├── SeatController.java
│   ├── SeatMapper.java
│   └── dto/
│       ├── SeatRequest.java
│       └── SeatResponse.java
├── booking/
│   ├── Booking.java
│   ├── BookingItem.java
│   ├── BookingStatus.java
│   ├── BookingRepository.java
│   ├── BookingService.java
│   ├── BookingServiceImpl.java     # the optimistic-locking reservation logic
│   ├── BookingController.java
│   ├── BookingMapper.java
│   └── dto/
│       ├── BookingRequest.java
│       ├── BookingResponse.java
│       └── BookingItemResponse.java
└── config/                    # cross-cutting configuration (empty for now)

src/main/resources/
├── application.yml
└── db/migration/
    ├── V1__baseline.sql
    ├── V2__domain_schema.sql             # users, venues, events, seats, bookings, booking_items
    └── V3__seat_optimistic_locking.sql   # adds seats.version

src/test/java/com/ahdyahmed/eventhub/
├── EventhubApplicationTests.java        # Testcontainers context + schema/entity consistency check
├── venue/
│   └── VenueServiceImplTest.java
└── event/
    ├── EventServiceImplTest.java
    └── dto/
        └── EventRequestValidationTest.java   # exercises both custom validators directly
```

Packages are organized **by feature (vertical slice)**, not by technical layer (i.e. no top-level `entity/`, `repository/`, `service/`, `controller/` packages holding everything). Each domain concept — `user`, `venue`, `event`, `seat`, `booking` — owns its own entity, repository, service, controller, and DTOs. This scales better than layer-first packaging once a domain has more than a handful of types.

## Roadmap

**Week 1 — Foundation & domain**
- [x] Day 1 — project scaffold, Postgres via Docker Compose, Flyway baseline, health check
- [x] Day 2 — domain entities (Venue, Event, Seat, User, Booking, BookingItem) + schema migration
- [x] Day 3 — layered CRUD (Controller → Service → Repository → DTO) for Venue & Event
- [x] Day 4 — paginated, sortable, dynamically filterable event search
- [x] Day 5 — bean validation, global exception handling, first unit tests

**Week 2 — Concurrency & caching**
- [x] Day 6 — booking creation flow with `@Version` optimistic locking on seats
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
- **`@SuperBuilder`, not `@Builder`, on every entity** — this was a bug caught by actually running `mvn test` on Day 5: plain `@Builder` only builds fields declared directly on the annotated class, so it silently ignored everything inherited from `BaseEntity` (`id`, `createdAt`, `updatedAt`) — the generated builders had no `.id(...)` method at all. `@SuperBuilder` walks the class hierarchy and needs to be applied consistently on the base class and every subclass, including a protected no-args constructor on `BaseEntity` so subclasses' JPA-required no-arg constructors still have a `super()` to call.
- **`price_at_booking` captured on `BookingItem` instead of read live from `Seat`** — a booking's price shouldn't silently change if the seat's price is edited later. Small detail, but it's the kind of thing that matters in a real order-processing system and costs nothing to get right now.
- **Manual mappers over MapStruct** — at 2 entities and small DTOs, a mapping library buys nothing but an annotation processor and generated code to explain. Revisit if the DTO surface grows significantly.
- **`EventMapper` maps to a local `VenueSummary`, not `venue.dto.VenueResponse`** — each feature package depends only on what it needs to expose, not on another feature's full response contract. If `VenueResponse` changes shape for venue-specific reasons, `EventResponse` doesn't move with it.
- **`ResourceNotFoundException` with `@ResponseStatus` instead of a `@ControllerAdvice` from the start** — gets correct 404s working today without building error-handling infrastructure before there's more than one exception type to handle consistently. Day 5 replaces it.
- **`Specification` over hand-written `@Query` methods for event search** — the alternative is either one giant native/JPQL query with a dozen optional `AND`s hidden behind string concatenation, or a combinatorial explosion of derived query methods for every filter combination. Specifications compose cleanly and each filter is independently testable.
- **A dedicated `PageResponse<T>` rather than serializing `Page<T>` directly** — keeps the API's pagination contract stable and readable regardless of which Spring Data version is on the classpath, and matches the "don't leak internals" principle already applied to entities in Day 3.
- **Two custom constraints instead of stretching built-ins to fit** — `@Future` doesn't express "needs lead time," and a `@Pattern` regex for category would bury the allowed-values list inside a hard-to-read regex instead of a named, reusable validator with a clear message.
- **One `ErrorResponse` shape for every exception, including validation failures** — a separate DTO for validation errors would mean clients need two error-parsing code paths instead of one with an optional field.
- **The catch-all `Exception` handler logs full detail server-side but returns a generic message to the client** — returning stack traces or exception class names in a 500 response is a real information-disclosure risk, not just unpolished output.
- **Mappers are used for real in service unit tests, not mocked** — `VenueMapper`/`EventMapper` have no dependencies and no side effects; mocking them would mean asserting against a canned `when(...)` response instead of the mapper's actual behavior, which defeats the point of the test.
- **`userId`/seat creation are plain, unauthenticated endpoints for now** — there's no security layer until Day 16. Booking takes a client-supplied `userId` rather than reading a security context that doesn't exist yet. This is a known, temporary gap, not an oversight — flagged in the code and here so it doesn't get mistaken for the final design.
- **Seats move to `RESERVED`, not `BOOKED`, on booking creation** — `BOOKED` is reserved for after the (currently nonexistent) payment step confirms the booking on Day 14. Modeling that intermediate state now, even before payment exists, keeps the seat lifecycle honest instead of pretending a booking is final before money has changed hands.
- **`saveAndFlush()` per seat instead of one flush at the end of the loop** — an optimistic-lock failure needs to be attributable to a specific seat. Batching every seat update into a single flush at the end would still catch the conflict correctly, but the exception wouldn't clearly indicate *which* seat lost the race in a multi-seat booking.
- **Business-state conflict and optimistic-lock conflict both map to the same `SeatUnavailableException`** — a client asking "can I book seat 5" doesn't need to know or care whether the answer came from a status check or a version mismatch. Both mean the same thing: pick a different seat.

# EventHub — Booking & Order Processing System

A production-grade event/ticket booking system demonstrating optimistic locking under concurrency, Redis caching, and event-driven order processing in Spring Boot. This is Project 3 of a 3-project backend portfolio (Core REST API → Auth & Authorization → **Production-grade Booking/Order System**).

**Status:** 🚧 Day 7 — concurrency proven under load. Caching, the event-driven pipeline, auth, and production hardening land over the following days (see [Roadmap](#roadmap) below).

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

Runs the fast suite: plain JUnit 5 + Mockito unit tests (no Docker needed) plus `EventhubApplicationTests`, which uses Testcontainers to boot the full Spring context against a real, disposable Postgres — so Docker must be running for that one to pass.

```bash
mvn verify
```

Also runs `BookingConcurrencyIT` — a slower Testcontainers-backed integration test that fires real concurrent HTTP requests at the app to prove the optimistic-locking behavior described in [What Day 7 adds](#what-day-7-adds). It's deliberately kept out of the fast `mvn test` path via Maven Failsafe (which handles `*IT` classes) rather than Surefire (which handles `*Test`/`*Tests`) — see the `pom.xml` comment on the `maven-failsafe-plugin` block for why.

## Configuration

All datasource settings are overridable via environment variables, with sane local defaults baked in:

| Variable       | Default          | Notes                                   |
|-----------------|-------------------|------------------------------------------|
| `DB_PORT`         | `5433`             | Matches the host port in `docker-compose.yml` |
| `DB_NAME`           | `eventhub_db`        |                                            |
| `DB_USER`             | `eventhub_user`        |                                            |
| `DB_PASSWORD`           | `eventhub_pass`          | Local dev only — never used as-is in a real deployment |
| `SERVER_PORT`             | `8080`                    |                                            |

## API reference

| Method | Path                               | Purpose                                              |
|--------|-------------------------------------|-------------------------------------------------------|
| POST   | `/api/v1/venues`                     | Create a venue                                         |
| GET    | `/api/v1/venues`                      | List all venues                                         |
| GET    | `/api/v1/venues/{id}`                  | Get one venue                                            |
| PUT    | `/api/v1/venues/{id}`                   | Update a venue                                            |
| DELETE | `/api/v1/venues/{id}`                    | Delete a venue                                             |
| POST   | `/api/v1/events`                          | Create an event (by `venueId`)                              |
| GET    | `/api/v1/events`                           | Paginated, filterable, sortable event search — see [Usage examples](#usage-examples) |
| GET    | `/api/v1/events/{id}`                       | Get one event                                                |
| PUT    | `/api/v1/events/{id}`                        | Update an event                                               |
| DELETE | `/api/v1/events/{id}`                         | Delete an event                                                |
| POST   | `/api/v1/users`                                | Create a user (stand-in until Day 16's real auth)               |
| GET    | `/api/v1/users/{id}`                            | Get one user                                                     |
| POST   | `/api/v1/events/{eventId}/seats`                 | Add a seat to an event                                            |
| GET    | `/api/v1/events/{eventId}/seats`                  | List an event's seats, optional `?status=` filter                 |
| POST   | `/api/v1/bookings`                                 | Create a booking — reserves one or more seats                      |
| GET    | `/api/v1/bookings/{id}`                             | Get one booking                                                     |

Every `POST`/`PUT` body is validated (`@Valid`); every error response — validation failure, not-found, conflict, or unexpected — comes back in the one shape `ErrorResponse` defines. See [Usage examples](#usage-examples) for what each looks like.

## Usage examples

**Full happy path — venue → event → seat → user → booking:**

```bash
curl -X POST http://localhost:8080/api/v1/venues -H "Content-Type: application/json" \
  -d '{"name":"Cairo Arena","city":"Cairo","address":"Nasr City","capacity":5000}'

curl -X POST http://localhost:8080/api/v1/events -H "Content-Type: application/json" \
  -d '{"venueId":1,"name":"Launch Night","description":"Opening event","category":"CONCERT","eventDate":"2026-12-01T19:00:00Z"}'

curl -X POST http://localhost:8080/api/v1/events/1/seats -H "Content-Type: application/json" \
  -d '{"seatNumber":"A1","section":"Floor","price":50.00}'

curl -X POST http://localhost:8080/api/v1/users -H "Content-Type: application/json" \
  -d '{"fullName":"Ahmed Test","email":"ahmed@example.com"}'

curl -X POST http://localhost:8080/api/v1/bookings -H "Content-Type: application/json" \
  -d '{"userId":1,"seatIds":[1]}'
```

**Trying to book the same seat again returns 409, not a 500** (and Day 7's `BookingConcurrencyIT` proves this holds even when the requests genuinely race, not just when they're sequential like this):

```bash
curl -i -X POST http://localhost:8080/api/v1/bookings -H "Content-Type: application/json" \
  -d '{"userId":1,"seatIds":[1]}'
```

**Paginated, filterable, sortable event search:**

```bash
# Defaults: 20 per page, soonest first
curl "http://localhost:8080/api/v1/events"

# Page 2, 5 per page
curl "http://localhost:8080/api/v1/events?page=1&size=5"

# Filter by city + category, sorted by date descending
curl "http://localhost:8080/api/v1/events?city=Cairo&category=CONCERT&sort=eventDate,desc"

# Date range filter
curl "http://localhost:8080/api/v1/events?fromDate=2026-01-01T00:00:00Z&toDate=2026-12-31T23:59:59Z"
```

**A validation failure:**

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

**A not-found:**

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
│   │   ├── SeatUnavailableException.java   # 409 - business-state or optimistic-lock conflict
│   │   ├── BookingValidationException.java # 400 - cross-field booking rules
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
│   ├── Seat.java                   # carries @Version
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
├── event/
│   ├── EventServiceImplTest.java
│   └── dto/
│       └── EventRequestValidationTest.java   # exercises both custom validators directly
└── booking/
    ├── BookingServiceImplTest.java      # unit: happy path + every failure mode, mocked repos
    └── BookingConcurrencyIT.java        # integration: real concurrent HTTP requests, real Postgres
```

Packages are organized **by feature (vertical slice)**, not by technical layer (i.e. no top-level `entity/`, `repository/`, `service/`, `controller/` packages holding everything). Each domain concept — `user`, `venue`, `event`, `seat`, `booking` — owns its own entity, repository, service, controller, and DTOs. This scales better than layer-first packaging once a domain has more than a handful of types.

## What Day 7 adds

Day 6 wrote the optimistic-locking defense; Day 7 is the test that actually proves it holds — up to and including firing real simultaneous HTTP requests at a live app, not asserting against a mocked call.

- **`BookingConcurrencyIT`** — spins up the full app on a random port against a real Testcontainers Postgres, seeds one contested seat, then fires 10 concurrent `POST /api/v1/bookings` requests at it from a 10-thread pool. A `CountDownLatch` pair holds every thread at the starting line until all 10 have reached it, then releases them in the same instant — that's what makes this genuinely concurrent rather than "fast sequential calls that happen not to overlap much."
- **What it asserts**: exactly 1 of the 10 requests gets a 2xx, the other 9 get `409` (not a 500, not a hang), and the seat's `version` column ends up at `1` — not `10` — meaning exactly one `UPDATE` actually succeeded at the database level. That last check is the one that would catch a subtle bug where the application *looks* correct but the locking isn't actually engaged (e.g. a missing `@Version` mapping, or a repository method that bypasses versioned updates).
- **`BookingServiceImplTest`** — the general-purpose unit tests `BookingServiceImpl` didn't get on Day 6: happy path, missing user, missing/partial seats, a seat that's already taken (the pre-check path), the optimistic-lock exception being translated correctly (the mocked-`saveAndFlush` path — proves the *translation* logic, not the *locking* itself, which only `BookingConcurrencyIT` can do), and seats spanning two different events.
- **`maven-failsafe-plugin`** added to `pom.xml` — `BookingConcurrencyIT` needs Docker and takes several seconds, so it's deliberately excluded from the fast `mvn test` path and only runs under `mvn verify`. See [Running the tests](#running-the-tests).
- **Container lifecycle fix**: the first version of `BookingConcurrencyIT` used the `@Testcontainers`/`@Container` annotation pair (same pattern as `EventhubApplicationTests`), and it failed on a real run with `ExtensionConfigurationException: Container postgres needs to be initialized` — thrown before the container even attempted to start. Switched to explicit `@BeforeAll`/`@AfterAll` calling `.start()`/`.stop()` directly, which removes JUnit's reflective field-scanning step entirely rather than trying to chase down why it failed in this one case but not the other.

**Run it:**

```bash
mvn verify
```

If it passes, the log will show 10 requests completed, 1 success, 9 conflicts, and a final seat version of 1 — that combination is the actual proof, not just "no exceptions were thrown."

## Roadmap

**Week 1 — Foundation & domain**
- [x] Day 1 — project scaffold, Postgres via Docker Compose, Flyway baseline, health check
- [x] Day 2 — domain entities (Venue, Event, Seat, User, Booking, BookingItem) + schema migration
- [x] Day 3 — layered CRUD (Controller → Service → Repository → DTO) for Venue & Event
- [x] Day 4 — paginated, sortable, dynamically filterable event search
- [x] Day 5 — bean validation, global exception handling, first unit tests

**Week 2 — Concurrency & caching**
- [x] Day 6 — booking creation flow with `@Version` optimistic locking on seats
- [x] Day 7 — concurrency test proving the race condition is handled correctly
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
- **Testcontainers over an in-memory database (e.g. H2) for tests** — tests run against the same database engine as production. This matters more here than in most projects because proving optimistic-locking behavior under real concurrency is the project's central claim, and an in-memory substitute wouldn't faithfully represent it.
- **Feature packages, not layer packages** — `user/`, `venue/`, `event/`, `seat/`, `booking/` instead of a flat `entity/` + `repository/` + `service/`. Keeps everything related to one domain concept in one place as the project grows.
- **No `@Version` on `Seat` until Day 6** — added next to the booking flow that actually exercises it, on purpose, so the commit that introduces concurrency control has something to show for it.
- **`equals`/`hashCode` based only on a non-null `id`, not Lombok's field-based default** — field-based equality breaks on Hibernate proxies and recurses infinitely across bidirectional associations (`Booking` ↔ `BookingItem`, etc.). `BaseEntity` implements the standard safe pattern once, and every entity inherits it.
- **`@SuperBuilder`, not `@Builder`, on every entity** — a bug caught by actually running `mvn test`: plain `@Builder` only builds fields declared directly on the annotated class, so it silently ignored everything inherited from `BaseEntity` (`id`, `createdAt`, `updatedAt`). `@SuperBuilder` walks the class hierarchy and needs to be applied consistently on the base class and every subclass, including a protected no-args constructor on `BaseEntity` so subclasses' JPA-required no-arg constructors still have a `super()` to call.
- **`price_at_booking` captured on `BookingItem` instead of read live from `Seat`** — a booking's price shouldn't silently change if the seat's price is edited later.
- **Manual mappers over MapStruct** — at this DTO surface size, a mapping library buys nothing but an annotation processor and generated code to explain. Revisit if the DTO surface grows significantly.
- **`EventMapper` maps to a local `VenueSummary`, not `venue.dto.VenueResponse`** — each feature package depends only on what it needs to expose, not on another feature's full response contract.
- **`Specification` over hand-written `@Query` methods for event search** — the alternative is either one giant query with a dozen optional `AND`s hidden behind string concatenation, or a combinatorial explosion of derived query methods. Specifications compose cleanly and each filter is independently testable.
- **A dedicated `PageResponse<T>` rather than serializing `Page<T>` directly** — keeps the API's pagination contract stable regardless of which Spring Data version is on the classpath, matching the "don't leak internals" principle applied to entities.
- **Two custom validators instead of stretching built-ins to fit** — `@Future` doesn't express "needs lead time," and a `@Pattern` regex for category would bury the allowed-values list inside a hard-to-read regex instead of a named, reusable validator with a clear message.
- **One `ErrorResponse` shape for every exception, including validation failures** — a separate DTO for validation errors would mean clients need two error-parsing code paths instead of one with an optional field.
- **The catch-all `Exception` handler logs full detail server-side but returns a generic message to the client** — returning stack traces or exception class names in a 500 response is an information-disclosure risk, not just unpolished output.
- **Mappers are used for real in service unit tests, not mocked** — they have no dependencies and no side effects; mocking them would mean asserting against a canned `when(...)` response instead of their actual behavior, defeating the point of the test.
- **`userId` is passed explicitly in the booking request body, not read from a security context** — there's no auth yet (Day 16). This is a known, temporary gap, flagged in the code so it doesn't get mistaken for the final design.
- **Seats move to `RESERVED`, not `BOOKED`, on booking creation** — `BOOKED` is reserved for after the (currently nonexistent) payment step confirms the booking on Day 14. Modeling that intermediate state now keeps the seat lifecycle honest instead of pretending a booking is final before money has changed hands.
- **`saveAndFlush()` per seat instead of one flush at the end of the loop** — an optimistic-lock failure needs to be attributable to a specific seat; batching every update into one flush would still catch the conflict but lose that attribution in a multi-seat booking.
- **Business-state conflict and optimistic-lock conflict both map to the same `SeatUnavailableException`** — a client asking "can I book seat 5" doesn't need to know whether the answer came from a status check or a version mismatch. Both mean the same thing: pick a different seat.
- **`TestRestTemplate` over `MockMvc` for the concurrency test** — `MockMvc` dispatches through a single thread by design, which would make "concurrent" requests concurrent in name only. A real embedded server hit by real threads is the only way to let the database's actual row-level locking decide the outcome, which is the entire thing under test.
- **A `CountDownLatch` pair to synchronize thread start, not just "submit 10 tasks to a pool"** — without an explicit starting line, a thread pool tends to run submitted tasks in close-but-not-simultaneous succession, which could pass even with a broken locking implementation. Holding every thread at a barrier until all are ready is what actually forces the race.
- **Asserting the seat's final `version` value, not just the HTTP status counts** — checking for "1 success, 9 conflicts" alone wouldn't catch a scenario where the locking silently no-ops; checking that `version` moved from `0` to exactly `1` confirms precisely one `UPDATE` reached the database, which is the real claim being tested.
- **`maven-failsafe-plugin` added to separate `*IT` from `*Test`** — `BookingConcurrencyIT` needs Docker and takes seconds, not milliseconds. Keeping it out of the default `mvn test` path (Surefire) and requiring `mvn verify` (Failsafe) keeps the everyday test loop fast without hiding the slower test from the project — Day 19's CI pipeline will run `mvn verify` specifically to include it.
- **Explicit `@BeforeAll`/`@AfterAll` container lifecycle in `BookingConcurrencyIT`, not `@Testcontainers`/`@Container`** — caught by actually running `mvn verify`: the annotation-based approach threw `ExtensionConfigurationException: Container postgres needs to be initialized` before the container even attempted to start, despite the identical pattern working in `EventhubApplicationTests`. Rather than chase the exact extension-ordering cause, calling `.start()`/`.stop()` directly sidesteps JUnit's reflective field-scanning step altogether — fewer moving parts, same result.

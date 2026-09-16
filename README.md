# EventHub — Booking & Order Processing System

A production-grade event/ticket booking system demonstrating optimistic locking under concurrency, Redis caching, and event-driven order processing in Spring Boot. This is Project 3 of a 3-project backend portfolio (Core REST API → Auth & Authorization → **Production-grade Booking/Order System**).

**Status:** 🚧 Day 8 — Redis cache-aside on the read-heavy endpoints. Cache invalidation on booking, the event-driven pipeline, auth, and production hardening land over the following days (see [Roadmap](#roadmap) below).

---

## Tech stack

| Concern            | Choice                                   |
|---------------------|-------------------------------------------|
| Language / runtime   | Java 21                                   |
| Framework            | Spring Boot 3.3.4 (Web, Data JPA, Validation, Actuator) |
| Database              | PostgreSQL 16                              |
| Migrations             | Flyway                                     |
| Caching                | Redis (cache-aside, from Day 8)            |
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

1. Start Postgres + Redis:
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
| `REDIS_HOST`              | `localhost`                |                                            |
| `REDIS_PORT`                | `6380`                        | Matches the host port in `docker-compose.yml` |
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

## Full end-to-end test flow

A single ordered walkthrough exercising everything built so far — infra, CRUD, search, concurrency, and caching — against a clean local stack. Run each block in order; later blocks assume the ids created by earlier ones (`venueId=1`, `eventId=1`, `seatId=1`, `userId=1`).

**1. Bring the stack up and confirm health**

```bash
docker compose up -d
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

**2. Venue → Event → Seat → User (core domain, Days 2–3)**

```bash
curl -X POST http://localhost:8080/api/v1/venues -H "Content-Type: application/json" \
  -d '{"name":"Cairo Arena","city":"Cairo","address":"Nasr City","capacity":5000}'

curl -X POST http://localhost:8080/api/v1/events -H "Content-Type: application/json" \
  -d '{"venueId":1,"name":"Launch Night","description":"Opening event","category":"CONCERT","eventDate":"2026-12-01T19:00:00Z"}'

curl -X POST http://localhost:8080/api/v1/events/1/seats -H "Content-Type: application/json" \
  -d '{"seatNumber":"A1","section":"Floor","price":50.00}'

curl -X POST http://localhost:8080/api/v1/users -H "Content-Type: application/json" \
  -d '{"fullName":"Ahmed Test","email":"ahmed@example.com"}'
```

**3. Paginated, filterable, sortable event search (Day 4)**

```bash
curl "http://localhost:8080/api/v1/events"
curl "http://localhost:8080/api/v1/events?page=0&size=5"
curl "http://localhost:8080/api/v1/events?city=Cairo&category=CONCERT&sort=eventDate,desc"
curl "http://localhost:8080/api/v1/events?fromDate=2026-01-01T00:00:00Z&toDate=2026-12-31T23:59:59Z"
```

**4. Validation + error handling (Day 5)**

```bash
# 400 - fails name/category/date validation at once
curl -i -X POST http://localhost:8080/api/v1/events -H "Content-Type: application/json" \
  -d '{"venueId":1,"name":"","category":"NOT_REAL","eventDate":"2020-01-01T00:00:00Z"}'

# 404 - consistent ErrorResponse shape for not-found too
curl -i http://localhost:8080/api/v1/events/999
```

**5. Booking + optimistic locking under a real race (Days 6–7)**

```bash
# Succeeds - reserves seat 1
curl -i -X POST http://localhost:8080/api/v1/bookings -H "Content-Type: application/json" \
  -d '{"userId":1,"seatIds":[1]}'

# Same seat again - clean 409, not a 500
curl -i -X POST http://localhost:8080/api/v1/bookings -H "Content-Type: application/json" \
  -d '{"userId":1,"seatIds":[1]}'
```

```bash
# The real proof: 10 threads racing the same seat simultaneously via
# BookingConcurrencyIT - 1 winner, 9 conflicts, seat version ends at 1
mvn verify
```

**6. Redis cache-aside (Day 8)**

```bash
# First call - Postgres (watch the DEBUG Hibernate SQL log line)
curl http://localhost:8080/api/v1/events/1
# Second call within 5m - Redis, no SQL log line
curl http://localhost:8080/api/v1/events/1

curl http://localhost:8080/api/v1/events/1/seats
curl http://localhost:8080/api/v1/events/1/seats

# Confirm what actually landed in Redis
docker exec eventhub-redis redis-cli KEYS '*'
docker exec eventhub-redis redis-cli GET 'events::1'

# A write evicts its own cache entries - immediately reflected, no wait
curl -X PUT http://localhost:8080/api/v1/events/1 -H "Content-Type: application/json" \
  -d '{"venueId":1,"name":"Launch Night (Rescheduled)","description":"Opening event","category":"CONCERT","eventDate":"2026-12-02T19:00:00Z"}'
curl http://localhost:8080/api/v1/events/1   # updated name comes back immediately
```

**7. Full test suite**

```bash
mvn test      # fast: unit tests + EventhubApplicationTests (Testcontainers Postgres)
mvn verify    # adds BookingConcurrencyIT - slower, needs Docker
```

**8. Cache invalidation on booking (Day 9)** — a fresh seat, so the cache starts clean:

```bash
curl -X POST http://localhost:8080/api/v1/events/1/seats -H "Content-Type: application/json" \
  -d '{"seatNumber":"A2","section":"Floor","price":50.00}'

curl http://localhost:8080/api/v1/events/1/seats                # caches the listing
docker exec eventhub-redis redis-cli KEYS 'seat-availability*'  # shows the cached key

curl -X POST http://localhost:8080/api/v1/bookings -H "Content-Type: application/json" \
  -d '{"userId":1,"seatIds":[2]}'                                # books seat A2 (id 2)

docker exec eventhub-redis redis-cli KEYS 'seat-availability*'  # empty - evicted immediately
curl http://localhost:8080/api/v1/events/1/seats                # A2 shows RESERVED, no 30s wait
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
└── config/
    └── CacheConfig.java        # @EnableCaching + per-cache-name Redis TTLs

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

## What Day 9 adds

The gap flagged since Day 8 gets closed: `BookingServiceImpl` now tells `seat-availability` when it's gone stale, instead of leaving that entirely to a 30-second TTL.

- **`BookingServiceImpl.evictSeatAvailabilityCache`** — after a booking successfully reserves its seats, this evicts the exact `seat-availability` keys that event's seats could be cached under: `eventId-null`, `eventId-AVAILABLE`, `eventId-RESERVED`, `eventId-BOOKED`. `validateSingleEvent` already guarantees every seat in one booking belongs to the same event, so one call covers the whole booking regardless of how many seats it reserved.
- **A different trade-off than Day 8's `event-search` eviction, on purpose** — `event-search`'s cache key depends on arbitrary filter and paging combinations with no fixed upper bound, so `allEntries = true` was the only sane option there. `seat-availability`'s key space is bounded — one `eventId` combined with `null` or one of `SeatStatus`'s three values, four keys total — so enumerating and evicting exactly those four is both possible and precise here. Worth naming as a deliberate difference, not an inconsistency: the same "evict everything vs. evict exactly this" decision, made two different ways because the two caches' key spaces are genuinely different shapes.
- **`CacheManager` injected directly into `BookingServiceImpl`, not `@CacheEvict`** — `@CacheEvict`'s SpEL key expressions can only see a method's own parameters, and the seat/event this eviction needs isn't one of `create`'s parameters (it's derived from the seats that got reserved). Reaching for the `CacheManager` API directly is the honest way to express "the key to evict depends on something computed mid-method," rather than contorting an annotation to do something it wasn't built for.
- **TTL reasoning finalized, not just set** — Day 8 picked `events`/`event-search`/`seat-availability` TTLs of 5m/1m/30s as a starting point with "real tuning lands Day 9" written into the comment. Now that eviction-on-write is the primary defense for every cache this app has, the TTLs are reframed as a backstop, not the mechanism: `seat-availability`'s 30s exists to self-heal a seat changed by something *outside* this app's service layer (a direct DB write, a future consumer), not to be the reason a booking's effect on availability shows up promptly — that's the eviction's job now. See the updated `CacheConfig` class doc.
- **`create_happyPath_evictsSeatAvailabilityCacheForTheEvent`** — a new unit test using a real `ConcurrentMapCacheManager` (not a mock) so the assertion is genuine cache state, not a `verify()` on a method call: pre-populates all four keys for the booked event plus one key for a different event, books a seat, and asserts the booked event's four keys are gone while the other event's entry is untouched.

**Try it** (with the stack up via `docker compose up -d`):

```bash
# Cache the seat listing, then book it, then confirm the cache actually
# noticed - no waiting out the 30s TTL
curl http://localhost:8080/api/v1/events/1/seats
docker exec eventhub-redis redis-cli KEYS 'seat-availability*'   # shows the cached key

curl -X POST http://localhost:8080/api/v1/bookings -H "Content-Type: application/json" \
  -d '{"userId":1,"seatIds":[1]}'

docker exec eventhub-redis redis-cli KEYS 'seat-availability*'   # empty - evicted, not waiting on TTL
curl http://localhost:8080/api/v1/events/1/seats                # RESERVED, immediately
```

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
- [x] Day 8 — Redis cache-aside on read-heavy event/seat endpoints
- [x] Day 9 — cache invalidation on booking/seat state change
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
- **Three separate cache names instead of one shared cache with one TTL** — `events`, `event-search`, and `seat-availability` age at genuinely different rates (seat status changes constantly relative to a venue's address), so giving them one TTL would mean either seat data going stale too slowly or event data being evicted needlessly often.
- **A `RedisCacheManagerBuilderCustomizer` bean instead of hand-building a `RedisCacheManager`** — this hooks into the `RedisCacheManager` Spring Boot's autoconfiguration already builds from `application.yml` (connection factory, default TTL from `spring.cache.redis.time-to-live`) rather than replacing it outright, so the per-cache overrides are additive instead of a second, competing source of truth for the Redis connection itself.
- **`GenericJackson2JsonRedisSerializer` for cache values, not the JDK's `SerializationPair.java()`** — Java serialization ties every cached value to the exact class bytecode that wrote it, which breaks the moment a DTO's fields change shape; JSON in Redis is also just readable with `redis-cli GET`, which matters for actually debugging this during development.
- **`allEntries = true` on `event-search`, and on `SeatServiceImpl.create`'s `seat-availability` eviction** — `event-search` is keyed by an arbitrary filter/page combination the writer doesn't fully control, so there's no single key to compute precisely. A newly-created seat is a similar case: it could belong to any of the cached `(eventId, status)` listings for that event, so `SeatServiceImpl.create` also clears broadly rather than trying to guess which. Contrast this with `BookingServiceImpl`'s eviction of the same cache (Day 9): there, the key space actually is small and known, so that path evicts precisely instead — see the Day 9 bullet below for why the two paths make different choices for the same cache.
- **The booking-vs-seat-cache gap was left in on Day 8, closed Day 9, not patched early** — `BookingServiceImpl` could have `@CacheEvict`'d seat availability from Day 8 onward; it deliberately didn't. This class of bug (a write in one service invalidating a cache another service reads) was worth its own day and its own commit rather than getting absorbed into "add caching" — see `BookingServiceImpl.evictSeatAvailabilityCache` and "What Day 9 adds" above for how it closed.
- **`GenericJackson2JsonRedisSerializer` is built with an explicit `ObjectMapper`, not its own no-arg constructor** — the no-arg constructor's internal mapper doesn't register `jackson-datatype-jsr310`, which silently broke every cached `Instant` field. This only surfaced running against a real Redis, not in any unit test, because nothing in the unit test suite serializes through the cache layer at all — a gap worth naming, not just fixing.
- **`DefaultTyping.EVERYTHING`, not `NON_FINAL`, for the cache's polymorphic type info** — every response DTO in this app is a `record`, which is implicitly `final`. `NON_FINAL` deliberately skips writing the `"@class"` type id for final classes, reasoning that the declared type is already unambiguous — true at the call site, but `RedisCache` reads everything back as plain `Object`, so the type id is the only thing telling Jackson what to reconstruct. `EVERYTHING` covers final classes too.
- **`Stream.toList()` avoided for anything that will be cached, `Collectors.toCollection(ArrayList::new)` used instead** — `Stream.toList()`'s immutable, JDK-internal return type can't be reliably reconstructed by Jackson's polymorphic type-id mechanism once it's round-tripped through Redis as raw JSON. `PageResponse.from` wraps its content in `new ArrayList<>(...)` for the same reason, defensively, even though `Page.map()`'s current list type happens to work.
- **A `CacheErrorHandler` that logs and continues, not one that's silent or one that's strict** — without it, a Redis hiccup fails the request even though the underlying write already succeeded in Postgres, which is a worse failure mode than caching simply not happening. The explicit trade-off, named rather than left implicit: this is exactly why the two bugs above weren't caught immediately — they'd been failing (and logging a warning) on every single cached request from the start.
- **`maven-failsafe-plugin` added to separate `*IT` from `*Test`** — `BookingConcurrencyIT` needs Docker and takes seconds, not milliseconds. Keeping it out of the default `mvn test` path (Surefire) and requiring `mvn verify` (Failsafe) keeps the everyday test loop fast without hiding the slower test from the project — Day 19's CI pipeline will run `mvn verify` specifically to include it.
- **Explicit `@BeforeAll`/`@AfterAll` container lifecycle in `BookingConcurrencyIT`, not `@Testcontainers`/`@Container`** — caught by actually running `mvn verify`: the annotation-based approach threw `ExtensionConfigurationException: Container postgres needs to be initialized` before the container even attempted to start, despite the identical pattern working in `EventhubApplicationTests`. Rather than chase the exact extension-ordering cause, calling `.start()`/`.stop()` directly sidesteps JUnit's reflective field-scanning step altogether — fewer moving parts, same result.
- **`BookingServiceImpl` evicts `seat-availability` with `CacheManager` directly, by enumerating exact keys, rather than `allEntries = true`** — unlike `event-search`'s unbounded key space, `seat-availability`'s is small and fixed: one `eventId` paired with `null` or one of `SeatStatus`'s three values. Enumerating those four keys is cheap and leaves every other event's cached listings alone, which `allEntries = true` wouldn't. The same cache, evicted two different ways by two different writers, for two different, equally deliberate reasons — see `SeatServiceImpl.create`'s eviction above for the other one.
- **A real `ConcurrentMapCacheManager` in `BookingServiceImplTest`, not a Mockito mock of `CacheManager`** — the thing worth proving is that specific keys actually became unreachable in a cache, which is state, not a method call. `verify(cacheManager).getCache(...)` would prove the code *tried* to evict something; asserting `cache.get(key)` is `null` afterward proves it actually happened, and a negative assertion on an untouched key (a different event's cache entry) proves the eviction is precise, not a lucky wildcard.
- **TTLs reframed, not re-tuned, once eviction-on-write existed for every cache** — Day 8 set `seat-availability`'s TTL to 30s as the *only* thing standing between a booking and a stale read. Once `BookingServiceImpl` evicts on every booking, that TTL's job changes: it's now a backstop against staleness from writers outside this app's own service layer, not the primary mechanism. The number didn't need to change; what it protects against did, and the comments in `CacheConfig` were rewritten to say so rather than leaving Day 8's now-inaccurate reasoning in place.

# EventHub — Booking & Order Processing System

A production-grade event/ticket booking system demonstrating optimistic locking under concurrency, Redis caching, and event-driven order processing in Spring Boot. This is Project 3 of a 3-project backend portfolio (Core REST API → Auth & Authorization → **Production-grade Booking/Order System**).

**Status:** 🚧 Day 11 — Kafka topology in place (KRaft mode), nothing publishing to it yet. Publishing `BookingConfirmedEvent`, the notification/payment consumers, auth, and production hardening land over the following days (see [Roadmap](#roadmap) below).

---

## Tech stack

| Concern            | Choice                                   |
|---------------------|-------------------------------------------|
| Language / runtime   | Java 21                                   |
| Framework            | Spring Boot 3.3.4 (Web, Data JPA, Validation, Actuator) |
| Database              | PostgreSQL 16                              |
| Migrations             | Flyway                                     |
| Caching                | Redis (cache-aside, from Day 8)            |
| Messaging               | Apache Kafka (KRaft mode, from Day 11)     |
| Testing                  | JUnit 5, Mockito, Testcontainers            |
| Build                     | Maven                                       |
| Containerization           | Docker / Docker Compose                     |
| CI                          | GitHub Actions (from Day 19)                |

## Prerequisites

- Java 21 (JDK)
- Maven 3.9+
- Docker + Docker Compose

## Running locally

1. Start Postgres + Redis + Kafka:
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

Also runs the two slower Testcontainers-backed integration tests, kept out of the fast `mvn test` path via Maven Failsafe (which handles `*IT` classes) rather than Surefire (which handles `*Test`/`*Tests`) — see the `pom.xml` comment on the `maven-failsafe-plugin` block for why:

- **`BookingConcurrencyIT`** — fires real concurrent HTTP requests at the app to prove the optimistic-locking behavior added Day 6/7 actually holds under a real race, not just in a mocked unit test.
- **`RedisCacheIT`** (Day 10) — a real Postgres *and* a real Redis, both via Testcontainers, proving the cache-aside behavior added Days 8–9: hit/miss (a second call to a `@Cacheable` method doesn't reach the database), and eviction-on-write (an update or a booking is reflected on the very next read, not after a TTL).

Both need Docker running to pass; `mvn verify` takes noticeably longer than `mvn test` as a result — two separate sets of containers spin up and tear down.

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
| `KAFKA_BOOTSTRAP_SERVERS`     | `localhost:9094`                | Matches the host port in `docker-compose.yml`, not Kafka's usual `9092` — see the Day 11 write-up for why |
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

**9. Kafka topology (Day 11)** — nothing publishes yet, so this just confirms the topic exists:

```bash
docker exec eventhub-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
# booking-confirmed-events
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
    ├── CacheConfig.java        # @EnableCaching + per-cache-name Redis TTLs
    └── KafkaTopicConfig.java   # topic topology (Day 11)

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

## What Day 11 adds

Week 3 starts, and with it a deliberate deviation from the original plan: the roadmap this project started from explicitly chose RabbitMQ over Kafka for the event-driven pipeline, reasoning that *"Kafka is heavier to justify unless you want the extra flex."* This project takes that flex on purpose — Kafka's a more common ask in job postings for this kind of role, and the two aren't equivalent enough that "I did it with RabbitMQ" fully substitutes. See Design decisions for the fuller trade-off, not just the swap.

- **`docker-compose.yml`** — a single-broker `apache/kafka:3.8.0` container in **KRaft mode**: no separate ZooKeeper container, because Kafka's own metadata quorum has been the officially supported mode since Kafka 3.7 and running one container instead of two is a straightforward win for a local dev stack. Host port `9094` (not Kafka's usual `9092`), same "don't clash with a locally running instance" reasoning as Postgres (`5433`) and Redis (`6380`) — except here the port also has to be repeated in `KAFKA_ADVERTISED_LISTENERS`, since Kafka's client protocol has the broker *tell* connecting clients which address to send requests to, and that address has to be the one actually reachable from outside the container.
- **`KafkaTopicConfig`** — one `NewTopic` bean, `booking-confirmed-events`, the equivalent of declaring a queue and its binding in RabbitMQ. Spring Boot's autoconfigured `KafkaAdmin` reconciles every `NewTopic` bean against the broker on startup; nothing in application code calls this class directly, the same way nothing calls a Flyway migration directly.
- **3 partitions, 1 replica — for two different reasons, not the same one** — partition count is fixed for a topic's lifetime in any way that matters (adding more later breaks the key→partition mapping any existing keyed messages relied on), so 3 is a realistic starting point chosen now rather than "1, because that's all this demo needs." Replica count of 1 isn't a similar simplification — it's a hard constraint of running exactly one broker locally; a real deployment would run this at 3, same as the `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR` etc. set in the broker's own config.
- **Producer serialization is JSON with type headers turned off** (`spring.json.add.type.headers: false`) — Spring Kafka's default `JsonSerializer` stamps every message with a `__TypeId__` header containing the producer's fully-qualified Java class name. That's an implementation detail of this app leaking onto the wire; a consumer inside this same app can deserialize a known event type from the topic name alone, the same way any other message contract works, without needing to know this app's package structure.
- **Topology only — nothing publishes yet.** No `BookingConfirmedEvent` class, no publish call in `BookingServiceImpl`. That's Day 12, deliberately kept separate: "the topic exists" and "something uses the topic" are different claims, and conflating them would make it harder to tell, a day from now, which day actually introduced a given piece of behavior.

**Verify the topology exists**, with the stack up via `docker compose up -d` (give Kafka ~15–20s to finish its KRaft startup before this):

```bash
mvn spring-boot:run
```

then, once it's started:

```bash
docker exec eventhub-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

should show `booking-confirmed-events` in the output — created by `KafkaAdmin` on application startup, not by anything you ran manually.

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
- [x] Day 10 — Testcontainers Redis test coverage

**Week 3 — Event-driven architecture**
- [x] Day 11 — Kafka setup + topology (KRaft mode, no ZooKeeper)
- [ ] Day 12 — publish `BookingConfirmedEvent`
- [ ] Day 13 — notification consumer
- [ ] Day 14 — mock payment step + booking status state machine
- [ ] Day 15 — retry/DLT for consumers + end-to-end event flow tests

**Week 4 — Production readiness**
- [ ] Day 16 — JWT auth + booking ownership checks, Actuator hardening
- [ ] Day 17 — structured JSON logging with correlation IDs
- [ ] Day 18 — multi-stage Dockerfile + full docker-compose stack (app + Postgres + Redis + Kafka)
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
- **`RedisCacheIT` proves invalidation by re-reading, not by checking Redis for an absent key** — asserting a key is gone from the cache proves the eviction call ran; it doesn't prove the *next read* is actually correct (a bug in the eviction's key computation could still leave the right cache entry, or drop the wrong one, while an unrelated key happens to also be absent). Calling the read again and asserting the response reflects the write is the stronger, more direct claim, and it's the one a caller of the API actually cares about.
- **`@SpyBean` on the JPA repositories to prove cache hit/miss, not a mock or a Redis key inspection** — the repository being called a second time is the literal definition of a cache miss; counting real invocations on the real bean proves that directly, without needing to reason about what a particular Redis command's output implies about application behavior.
- **`RedisCacheIT` adds a second Testcontainers container class rather than reusing `BookingConcurrencyIT`'s Postgres instance** — each `@SpringBootTest` class gets its own Spring context and its own container lifecycle by design in this codebase (see `BookingConcurrencyIT`'s and `EventhubApplicationTests`' own containers); sharing a container across test classes is a legitimate optimization some projects make, but it wasn't worth the added lifecycle-coordination complexity for a portfolio-sized suite where `mvn verify` running a bit longer is a fully acceptable trade-off.
- **Kafka instead of RabbitMQ, deviating from the original plan on purpose** — the roadmap this project started from picked RabbitMQ specifically because *"Kafka is heavier to justify unless you want the extra flex."* Taking that flex anyway is a deliberate trade: Kafka's operational model (partitions, consumer groups, offsets, log retention instead of a queue that empties) is a different, and for many roles a more commonly asked-about, mental model than RabbitMQ's — and it's worth being able to speak to both models' trade-offs rather than just one. The honest cost, named rather than hidden: RabbitMQ's routing (exchanges, bindings, routing keys) maps more directly onto "notify these different consumers about this one event" than Kafka's topic-and-partition model does, so some of what would have been exchange/binding configuration in Week 3 will instead show up as consumer group and topic design decisions. Both are legitimate ways to solve the same problem; this project is just solving it with the other one.
- **KRaft mode, not Kafka + ZooKeeper** — a second container (`bitnami/zookeeper` or similar) to coordinate a *single* broker would have added an entire extra moving part for no operational benefit this project actually needs. KRaft has been Kafka's officially supported mode without ZooKeeper since 3.7, so this isn't a shortcut relative to how Kafka is actually run today — running ZooKeeper here would be the outdated choice, not the safe one.
- **Kafka's host port is 9094, and that same number has to appear twice, not once** — Postgres and Redis only needed their non-default host port set in one place (the `ports:` mapping); Kafka's client protocol requires the broker to also declare that same address in `KAFKA_ADVERTISED_LISTENERS`, because after a client's initial connection, the broker's metadata response tells that client which address to use for every subsequent request. Get the two out of sync and the container looks like it started fine, right up until the app tries to actually produce anything and fails to reach the address it was told to use.
- **3 partitions declared now, even against a single local broker with nothing publishing yet** — partition count is fixed for a topic's practical lifetime (raising it later reshuffles which partition a given key lands on, breaking ordering guarantees for anything already relying on the old mapping), so it's a decision worth making deliberately once, now, rather than defaulting to 1 and having to revisit it under real load later. Replica count of 1 is not the same kind of decision — it's simply the largest number a single-broker cluster can support, not a preference.

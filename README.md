# EventHub — Booking & Order Processing System

A production-grade event/ticket booking system demonstrating optimistic locking under concurrency, Redis caching, and event-driven order processing in Spring Boot. This is Project 3 of a 3-project backend portfolio (Core REST API → Auth & Authorization → **Production-grade Booking/Order System**).

**Status:** 🚧 Day 18, Part 1 of 3 — the app now has a multi-stage, non-root, layered Docker image. Wiring it into `docker compose up` (Part 2) and the smoke test and docs (Part 3) come next. CI, CD, and load testing follow (see [Roadmap](#roadmap) below).

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
| Auth                     | Spring Security + stateless JWT (HS256, jjwt), from Day 16 |
| Logging                   | SLF4J/Logback, JSON via `logstash-logback-encoder`, MDC correlation IDs (from Day 17) |
| Container                 | Multi-stage Docker build, JRE-only Alpine runtime image, non-root (Day 18) |
| Testing                  | JUnit 5, Mockito, Testcontainers (Postgres, Kafka), Awaitility |
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

Logs are one JSON object per line by default (structured, with a correlation ID on every line). For readable plain-text output while developing:

```bash
SPRING_PROFILES_ACTIVE=pretty mvn spring-boot:run
```

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
| `JWT_SECRET`                | a local-dev placeholder      | **Must** be overridden in any real deployment — the default is committed to source control. Needs 32+ bytes for HS256 |
| `JWT_EXPIRATION_MS`           | `3600000` (1 hour)             | Token lifetime                              |
| `DB_HOST`                      | `localhost`                      | Postgres host. Added Day 18: inside a container this is the Postgres service's name, not `localhost` |
| `SPRING_PROFILES_ACTIVE`        | *(unset)*                        | Unset = JSON logs. `pretty` = plain-text, human-readable console logs (Day 17) |

## API reference

| Method | Path                               | Auth required | Purpose                                              |
|--------|-------------------------------------|:---:|-------------------------------------------------------|
| POST   | `/api/v1/auth/register`              | No | Create a user + password, get a token back              |
| POST   | `/api/v1/auth/login`                  | No | Exchange email + password for a token                   |
| POST   | `/api/v1/venues`                     | Yes | Create a venue                                         |
| GET    | `/api/v1/venues`                      | No | List all venues                                         |
| GET    | `/api/v1/venues/{id}`                  | No | Get one venue                                            |
| PUT    | `/api/v1/venues/{id}`                   | Yes | Update a venue                                            |
| DELETE | `/api/v1/venues/{id}`                    | Yes | Delete a venue                                             |
| POST   | `/api/v1/events`                          | Yes | Create an event (by `venueId`)                              |
| GET    | `/api/v1/events`                           | No | Paginated, filterable, sortable event search — see [Usage examples](#usage-examples) |
| GET    | `/api/v1/events/{id}`                       | No | Get one event                                                |
| PUT    | `/api/v1/events/{id}`                        | Yes | Update an event                                               |
| DELETE | `/api/v1/events/{id}`                         | Yes | Delete an event                                                |
| GET    | `/api/v1/users/{id}`                            | Yes | Get one user                                                     |
| POST   | `/api/v1/events/{eventId}/seats`                 | Yes | Add a seat to an event                                            |
| GET    | `/api/v1/events/{eventId}/seats`                  | No | List an event's seats, optional `?status=` filter                 |
| POST   | `/api/v1/bookings`                                 | Yes | Create a booking — reserves one or more seats, for the authenticated caller |
| GET    | `/api/v1/bookings/{id}`                             | Yes | Get one booking — 403 if it isn't yours                             |
| POST   | `/api/v1/bookings/{id}/cancel`                       | Yes | Cancel your own booking, releasing its seats             |

"No" means the endpoint is reachable without a token (public browsing, or the two endpoints whose whole job is to hand one out); everything marked "Yes" needs `Authorization: Bearer <token>` from `/api/v1/auth/login` or `/api/v1/auth/register`, or a `401` comes back instead. See [Authenticated flow](#authenticated-flow-register-book-cancel) for a runnable example and [SecurityConfig](#project-structure)'s own reasoning for exactly where that line sits.

Every `POST`/`PUT` body is validated (`@Valid`); every error response — validation failure, not-found, conflict, unauthorized, forbidden, or unexpected — comes back in the one shape `ErrorResponse` defines. See [Usage examples](#usage-examples) for what each looks like.

**Correlation IDs (Day 17):** every response — success or error — carries an `X-Correlation-Id` header, and every error body repeats it as `correlationId`. Send your own `X-Correlation-Id` (letters, digits, `.`, `_`, `-`, up to 64 characters) and it's reused; anything else is replaced with a generated UUID. It's the string to quote when reporting a problem, and the one to search the logs for.

**A note on the examples below:** sections through Day 15 predate authentication — their `curl` commands post a `userId` field with no `Authorization` header and won't run as-is against this version. The [Authenticated flow](#authenticated-flow-register-book-cancel) section has the current, runnable register → login → book → cancel flow.

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
  "correlationId": "5b0c7e1e-3f0a-4c1d-9a55-2f7d7f1f6a11",
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
  "correlationId": "0d4a5d2e-7c1b-4b8e-8e0f-9a3b6c2d1e77",
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

**9. Kafka topology (Day 11)** — confirms the topic exists:

```bash
docker exec eventhub-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
# booking-confirmed-events
```

**10. Booking event published (Day 12)** — a fresh booking, then read it back from the topic:

```bash
curl -X POST http://localhost:8080/api/v1/events/1/seats -H "Content-Type: application/json" \
  -d '{"seatNumber":"A3","section":"Floor","price":50.00}'

curl -X POST http://localhost:8080/api/v1/bookings -H "Content-Type: application/json" \
  -d '{"userId":1,"seatIds":[3]}'                                # books seat A3 (id 3)

docker exec eventhub-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic booking-confirmed-events --from-beginning --max-messages 1
# {"bookingId":..,"userId":1,"userEmail":"...","eventId":1,"seatIds":[3],"totalAmount":50.00,"confirmedAt":"..."}
```

**11. Notification consumer reacts independently (Day 13)** — same booking call as above; check the app's own console output, not Kafka's, for a line like:

```
Mock email -> ahmed@example.com: your booking .. for event 1 (1 seat(s), total 50.00) is confirmed
```

Nothing in the `curl` request or `BookingServiceImpl` triggers that line directly — `NotificationListener` picked it up off the topic on its own.

**12. Mock payment resolves the booking (Day 14)** — same booking as step 11 above; check the booking a moment later:

```bash
curl -X POST http://localhost:8080/api/v1/events/1/seats -H "Content-Type: application/json" \
  -d '{"seatNumber":"A4","section":"Floor","price":50.00}'

curl -X POST http://localhost:8080/api/v1/bookings -H "Content-Type: application/json" \
  -d '{"userId":1,"seatIds":[4]}'
# {"id":..,"status":"PENDING",...}  <- right after the call, payment hasn't landed yet

sleep 2
curl http://localhost:8080/api/v1/bookings/<id-from-above>
# {"id":..,"status":"CONFIRMED",...}  <- PaymentProcessedListener moved it, asynchronously

docker exec eventhub-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic payment-processed-events --from-beginning --max-messages 1
# {"bookingId":..,"eventId":1,"seatIds":[4],"totalAmount":50.00,"status":"SUCCEEDED","reason":null,"processedAt":"..."}
```

To see the decline branch instead, book a seat priced above `payment.mock.decline-threshold` (default `1000.00`) — the booking settles on `FAILED` and the seat referenced above goes back to `AVAILABLE` (confirm with `GET /api/v1/events/1/seats`) instead of staying `RESERVED` forever.

### Authenticated flow: register, book, cancel

Since Day 16 every booking endpoint needs a token. With the stack up and the app running:

```bash
# 1. register (returns a token immediately) - or POST /api/v1/auth/login later
curl -s -X POST http://localhost:8080/api/v1/auth/register -H "Content-Type: application/json" \
  -d '{"fullName":"Sara","email":"sara@example.com","password":"correct-horse-battery"}'
# {"token":"eyJ...","userId":1,"fullName":"Sara","email":"sara@example.com"}

TOKEN=<paste the token from above>

# 2. create seats (needs auth now) and book one - note: no userId in the body anymore
curl -X POST http://localhost:8080/api/v1/events/1/seats -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"seatNumber":"B1","section":"Floor","price":50.00}'

curl -X POST http://localhost:8080/api/v1/bookings -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"seatIds":[7]}'

# 3. cancel it - the seat goes back to AVAILABLE
curl -X POST http://localhost:8080/api/v1/bookings/<id>/cancel -H "Authorization: Bearer $TOKEN"

# 4. the things that should fail
curl -i http://localhost:8080/api/v1/bookings/<id>                     # 401 - no token
curl -i http://localhost:8080/actuator/metrics                         # 401 - metrics are locked
curl -i http://localhost:8080/actuator/health                          # 200 - probes stay open
# register a second user, then GET the first user's booking with their token -> 403
```

## Project structure

```
Dockerfile                   # Day 18 - multi-stage build: Maven build stage -> JRE-only, non-root runtime image
.dockerignore                # Day 18 - keeps target/, .git, IDE files and .env out of the build context
docker-compose.yml           # Postgres, Redis, Kafka (the app joins in Day 18 Part 2)
pom.xml

src/main/java/com/ahdyahmed/eventhub/
├── EventhubApplication.java   # entry point
├── common/
│   ├── BaseEntity.java             # shared id + audit columns, JPA-safe equals/hashCode
│   ├── dto/
│   │   └── PageResponse.java       # framework-agnostic pagination wrapper
│   ├── logging/                    # Day 17
│   │   ├── CorrelationId.java                  # header name + validate-or-generate rules
│   │   ├── MdcKeys.java                        # correlationId / userId MDC key names
│   │   ├── LogEvents.java                      # stable "event" names for lifecycle log lines
│   │   ├── CorrelationIdFilter.java            # assigns/echoes/clears the id, ahead of Spring Security
│   │   ├── RequestLoggingFilter.java           # one access-log line per request
│   │   ├── CorrelationIdProducerInterceptor.java  # MDC -> Kafka record header
│   │   └── CorrelationIdRecordInterceptor.java    # Kafka record header -> MDC
│   ├── exception/
│   │   ├── ResourceNotFoundException.java
│   │   ├── SeatUnavailableException.java   # 409 - business-state or optimistic-lock conflict
│   │   ├── BookingValidationException.java # 400 - cross-field booking rules
│   │   ├── InvalidBookingStateTransitionException.java  # 409 - illegal BookingStatus move (Day 14)
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
│   ├── UserMapper.java             # toResponse only - registration moved to auth/ (Day 16)
│   └── dto/
│       └── UserResponse.java
├── auth/                           # Day 16
│   ├── AuthController.java         # POST /register, POST /login - the only public write endpoints
│   ├── AuthService.java
│   ├── AuthServiceImpl.java        # the one place a raw password becomes a stored hash
│   ├── UserPrincipal.java          # Spring Security UserDetails wrapper around User
│   ├── EventHubUserDetailsService.java
│   ├── JwtService.java             # sign + verify HS256 tokens (jjwt)
│   ├── JwtProperties.java          # typed binding for security.jwt.*
│   ├── JwtAuthenticationFilter.java       # Bearer token -> SecurityContext, once per request
│   ├── JwtAuthenticationEntryPoint.java   # 401s from the filter chain in the API's own error shape
│   └── dto/
│       ├── RegisterRequest.java
│       ├── LoginRequest.java
│       └── AuthResponse.java
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
│   ├── BookingStateMachine.java           # Day 14 - legal status transitions, formalized
│   ├── SeatAvailabilityCacheEvictor.java  # Day 14 - eviction logic shared by booking + payment
│   ├── PaymentProcessedListener.java      # Day 14 - @KafkaListener that drives the state machine
│   ├── BookingRepository.java
│   ├── BookingService.java
│   ├── BookingServiceImpl.java     # the optimistic-locking reservation logic
│   ├── BookingController.java
│   ├── BookingMapper.java
│   ├── dto/
│   │   ├── BookingRequest.java
│   │   ├── BookingResponse.java
│   │   └── BookingItemResponse.java
│   └── event/                      # Day 12
│       ├── BookingConfirmedEvent.java
│       └── BookingConfirmedEventPublisher.java   # AFTER_COMMIT bridge to Kafka
├── notification/                   # Day 13
│   └── NotificationListener.java   # @KafkaListener - the decoupling proof point
├── payment/                        # Day 14
│   ├── PaymentService.java         # mock-charge contract
│   ├── PaymentResult.java
│   ├── PaymentStatus.java
│   ├── MockPaymentServiceImpl.java # deterministic threshold-based mock charge
│   ├── PaymentConsumer.java        # @KafkaListener - booking-confirmed-events in, payment-processed-events out
│   └── event/
│       └── PaymentProcessedEvent.java
└── config/
    ├── CacheConfig.java                  # @EnableCaching + per-cache-name Redis TTLs
    ├── KafkaTopicConfig.java             # topic topology (Day 11), now 4 topics (2 + their DLTs)
    ├── PaymentEventsConsumerConfig.java  # Day 14 - dedicated consumer factory for PaymentProcessedEvent
    ├── KafkaErrorHandlingConfig.java     # Day 15 - shared retry + dead-letter-topic policy
    └── SecurityConfig.java               # Day 16 - stateless JWT filter chain, public vs. protected routes

src/main/resources/
├── application.yml
├── logback-spring.xml                    # Day 17 - JSON console logs (default) / plain text (`pretty` profile)
└── db/migration/
    ├── V1__baseline.sql
    ├── V2__domain_schema.sql             # users, venues, events, seats, bookings, booking_items
    ├── V3__seat_optimistic_locking.sql   # adds seats.version
    └── V4__add_user_password.sql         # Day 16 - users.password_hash

src/test/java/com/ahdyahmed/eventhub/
├── EventhubApplicationTests.java        # Testcontainers context + schema/entity consistency check
├── venue/
│   └── VenueServiceImplTest.java
├── event/
│   ├── EventServiceImplTest.java
│   └── dto/
│       └── EventRequestValidationTest.java   # exercises both custom validators directly
├── booking/
│   ├── BookingServiceImplTest.java      # unit: happy path + every failure mode, mocked repos
│   ├── BookingConcurrencyIT.java        # integration: real concurrent HTTP requests, real Postgres
│   ├── BookingStateMachineTest.java     # Day 14 - every legal/illegal transition, including terminal states
│   └── PaymentProcessedListenerTest.java # Day 14 - status + seat + cache effects, mocked repo
└── payment/
    ├── MockPaymentServiceImplTest.java  # Day 14 - deterministic threshold behavior
    └── PaymentConsumerTest.java         # Day 14 - charge result -> published event mapping
auth/
├── JwtServiceTest.java             # Day 16 - round trip, wrong user, expiry, wrong secret
├── AuthServiceImplTest.java        # Day 16 - register hashes the password; login/duplicate errors propagate
└── JwtAuthenticationFilterTest.java # Day 17 - userId reaches the MDC for a valid token, never for a bad one
common/
├── exception/
│   └── ErrorResponseTest.java      # Day 17 - correlationId picked up from the MDC
└── logging/
    ├── CorrelationIdFilterTest.java            # Day 17 - generate/echo/reject-hostile/always-clear
    ├── RequestLoggingFilterTest.java           # Day 17 - levels, no query string, exception path
    └── CorrelationIdKafkaInterceptorsTest.java # Day 17 - producer stamp, consumer restore, never overwrite
integration/
└── EventChainIT.java   # Day 15 - the full booking -> payment event -> notification event chain,
                         # plus a forced-failure retry-then-dead-letter test, against real Postgres + Kafka
```

Packages are organized **by feature (vertical slice)**, not by technical layer (i.e. no top-level `entity/`, `repository/`, `service/`, `controller/` packages holding everything). Each domain concept — `user`, `venue`, `event`, `seat`, `booking` — owns its own entity, repository, service, controller, and DTOs. This scales better than layer-first packaging once a domain has more than a handful of types.

## What Day 18 adds

The roadmap's own words: "Multi-stage `Dockerfile` for the app (small final image). Finalize `docker-compose.yml`: app + postgres + redis + rabbitmq, one `docker compose up` should run everything." (This project uses Kafka, not RabbitMQ — so the stack is app + Postgres + Redis + Kafka.)

Day 18 is built in three parts. **Part 1 is done; Parts 2 and 3 are not started.**

- [x] **Part 1 — the image.** Multi-stage `Dockerfile` + `.dockerignore`, and the one config change the app needed to run in a container.
- [ ] **Part 2 — the stack.** Add the app to `docker-compose.yml`, health-gated startup order, Kafka reachable from both the host and other containers.
- [ ] **Part 3 — polish.** `.env.example`, a full-stack smoke test, docs.

### Part 1: what's in it

- **`Dockerfile`, two stages.** *Build*: `maven:3.9-eclipse-temurin-21` compiles and packages the app, then splits the jar into Spring Boot's layers. *Runtime*: `eclipse-temurin:21-jre-alpine` — JRE only, no Maven, no compiler — receives just those layers. Only the runtime stage is the shipped image.
- **Layered, so rebuilds are fast.** Dependencies, loader, snapshot dependencies and application code are separate image layers, ordered least to most volatile. A code-only change rebuilds a layer of a few hundred KB and reuses the cached dependency layer; the pom is copied and resolved *before* the source for the same reason.
- **Runs as a non-root user** (`eventhub`, uid 1001).
- **`HEALTHCHECK`** against `/actuator/health/liveness` (public, no token) using Alpine's built-in `wget`.
- **Container-aware JVM** — heap sized as a percentage of the container's memory limit, and the JVM exits on `OutOfMemoryError` so an orchestrator can restart it.
- **`.dockerignore`** keeps `target/`, `.git`, IDE files and any `.env` out of the build context.
- **`DB_HOST`** added to `application.yml`. The database host used to be hardcoded to `localhost`; inside a container that is the app's own container, not Postgres. The default is unchanged, so running locally behaves exactly as before.

### Try Part 1

```bash
# Build (first build downloads dependencies; later ones reuse the cache)
docker build -t eventhub-booking-system:dev .

# See the size, and the layer split
docker images eventhub-booking-system:dev
docker history eventhub-booking-system:dev

# Confirm it doesn't run as root
docker run --rm --entrypoint id eventhub-booking-system:dev
# uid=1001(eventhub) gid=1001(eventhub) ...
```

To actually start it today, bring up the infrastructure and point the container at it. On **Linux**, host networking lets the app's defaults (Postgres `5433`, Redis `6380`, Kafka `9094`) work unchanged:

```bash
docker compose up -d
docker run --rm --network host eventhub-booking-system:dev
curl http://localhost:8080/actuator/health/liveness     # {"status":"UP"}
```

Docker Desktop (macOS/Windows) doesn't support `--network host` the same way, and Kafka's advertised address (`localhost:9094`) isn't reachable from inside another container anyway. Making the full stack work on every platform is exactly what Part 2 is for — run the app locally with `mvn spring-boot:run` until then.

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
- [x] Day 12 — publish `BookingConfirmedEvent`
- [x] Day 13 — notification consumer
- [x] Day 14 — mock payment step + booking status state machine
- [x] Day 15 — retry/DLT for consumers + end-to-end event flow tests

**Week 4 — Production readiness**
- [x] Day 16 — JWT auth + booking ownership checks, Actuator hardening
- [x] Day 17 — structured JSON logging with correlation IDs
- [ ] Day 18 — multi-stage Dockerfile + full docker-compose stack (app + Postgres + Redis + Kafka) — *in progress: Part 1 of 3 (the image) done*
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
- **`userId` was passed explicitly in the booking request body through Day 15** — there was no auth yet, and this was a known, temporary gap flagged in the code so it wouldn't get mistaken for the final design. Closed on Day 16: `BookingRequest` no longer has the field at all; the owner comes from the authenticated principal (see Day 16's bullets below).
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
- **The booking-vs-seat-cache gap was left in on Day 8, closed Day 9, not patched early** — `BookingServiceImpl` could have `@CacheEvict`'d seat availability from Day 8 onward; it deliberately didn't. This class of bug (a write in one service invalidating a cache another service reads) was worth its own day and its own commit rather than getting absorbed into "add caching" — see `BookingServiceImpl.evictSeatAvailabilityCache` for how it closed.
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
- **`@TransactionalEventListener(AFTER_COMMIT)`, not a direct `KafkaTemplate.send()` inside `@Transactional`** — this is the specific problem the roadmap named for this day: publishing before a transaction commits means a consumer can react to a booking that later rolls back and never actually happened. Deferring the actual send to after commit turns "don't publish something that gets rolled back" from a race this code would otherwise have to reason about into a guarantee the framework enforces — the listener plainly cannot run for a transaction that didn't commit.
- **Not a transactional outbox, and that gap is written down rather than left implicit** — `AFTER_COMMIT` closes the "published something false" problem but not the "silently published nothing" one: a crash between the commit and the Kafka send completing loses the event with nothing to detect or replay it. An outbox table plus a poller closes that gap for real, at the cost of genuine additional infrastructure. The roadmap treats that infrastructure as a stretch goal for a project this size, not a requirement — accepting the gap is the documented choice, not an unnoticed one.
- **One record (`BookingConfirmedEvent`) serves as both the Spring `ApplicationEvent` payload and the Kafka message body** — a separate internal-event class and external-message class would need to be kept in sync by hand every time either one changed, with nothing enforcing that they actually stayed in sync. One shape used for both makes that drift structurally impossible instead of merely unlikely.
- **The Kafka message key is `bookingId`, not `eventId` or `userId`** — Kafka only guarantees ordering within a partition, and the key decides which partition a message lands on. Keying by `bookingId` means every message about one specific booking — this `BookingConfirmedEvent` today, and whatever payment or cancellation events later days add for the same booking — is guaranteed to arrive at any one consumer in the order it was sent. Keying by `eventId` would group differently (all bookings for one concert together) at the cost of losing per-booking ordering guarantees; neither is "more correct," they answer different questions, and per-booking ordering is the one this pipeline actually needs.
- **Per-listener `groupId`, not one shared `spring.kafka.consumer.group-id` default** — a consumer group is Kafka's load-balancing unit: consumers *in the same group* split a topic's partitions between them, so only one gets any given message. That's correct for scaling one logical consumer horizontally, and wrong the moment a second, independent consumer needs to see every message too. `notification-service` gets its own group id specifically so Day 14's payment consumer (its own, different group id) isn't competing with it for the same messages — both need to see everything, not split it.
- **`userEmail` added to `BookingConfirmedEvent` rather than giving `NotificationListener` a `UserRepository`** — the consumer needing contact info is a real requirement; reaching into the booking service's own database to get it is the wrong way to satisfy that requirement, because it means this "independent" consumer secretly isn't independent — it can't exist without booking's schema, booking's database credentials, and booking's uptime. Putting what a consumer needs directly on the event keeps the decoupling this whole day exists to prove actually true, not just true in the parts that are convenient.
- **No retry or dead-letter handling for the consumer through Day 14** — an exception in `onBookingConfirmed` was silently swallowed by Spring Kafka's default error handling; the notification for that one booking was simply lost. Flagged here and in the class doc rather than fixed immediately, so it stayed visible as a known gap instead of being discoverable only by triggering it — closed project-wide on Day 15 (see that section's bullets above for how).
- **`MockPaymentServiceImpl` declines by a fixed threshold, not a random chance** — a coin-flip mock exercises both branches too, but makes every demo, log walk, and test run non-reproducible. Keying the decision off `totalAmount` means "book something under `payment.mock.decline-threshold`" and "book something over it" are two reliable, repeatable ways to walk either path on demand — the same reasoning behind Day 7's `CountDownLatch` barrier instead of a hopeful thread pool.
- **`PaymentProcessedEvent` is one shape with a `status` field, not two separate event types (`PaymentSucceededEvent`/`PaymentFailedEvent`)** — one topic, one consumer method, with the branch happening in code that can see both outcomes together (and treat a redelivery of either the same way), rather than two independent listener methods that would need to agree by convention on things like "always evict the cache" instead of it being structurally guaranteed by one shared method body.
- **`BookingStateMachine.transition()` treats a same-status request as a no-op, not an error** — Kafka's at-least-once delivery guarantee means `PaymentProcessedListener` can legitimately see the same `PaymentProcessedEvent` twice (a consumer restart mid-processing, a rebalance). The second delivery finding the booking already `CONFIRMED` is expected, ordinary behavior; throwing `InvalidBookingStateTransitionException` for it would mean logging a scary-looking 409-shaped error for something that isn't actually wrong.
- **`CANCELLED` transitions declared in `BookingStateMachine` on Day 14, before anything could reach them (Day 16 added the caller)** — same reasoning as `BookingStatus.CANCELLED` and `SeatStatus.BOOKED` themselves being declared back on Day 2 before anything set them: Day 16's planned cancellation endpoint needs `PENDING → CANCELLED` and `CONFIRMED → CANCELLED` to already be legal moves, not a schema/lifecycle change bundled into that day's actual scope (auth).
- **A dedicated `ConsumerFactory`/`ConcurrentKafkaListenerContainerFactory` for `PaymentProcessedListener`, rather than widening the global default type** — `application.yml`'s `spring.json.value.default.type` is a single value shared by every listener on the default factory; it was sufficient through Day 13 because `NotificationListener` and `PaymentConsumer` (Day 14) both read `BookingConfirmedEvent` off the same topic. `PaymentProcessedListener` reads a different shape off a different topic, so it needed its own factory rather than the two event types contending over one shared property — a second `@KafkaListener` container factory is the smallest change that resolves that, reusing `KafkaProperties.buildConsumerProperties()` so only the one property that actually differs is overridden.
- **Seats move to `BOOKED` on `CONFIRMED`, and back to `AVAILABLE` (not left `RESERVED`) on `FAILED`** — `RESERVED` was always meant to be the short-lived state between "seat picked" and "payment resolved," per `SeatStatus`'s own Day 2/6 doc comments. Leaving a seat `RESERVED` forever after a declined mock charge would mean a seat some other customer could legitimately book instead sits unusable indefinitely — releasing it is what "the payment failed" should actually mean for seat availability, not just for the booking record.
- **`SeatAvailabilityCacheEvictor` extracted as a shared `@Component` rather than duplicated** — Day 9's private `evictSeatAvailabilityCache` method on `BookingServiceImpl` gained a second caller the moment `PaymentProcessedListener` needed the identical eviction after payment resolves seats. Two copies of "enumerate every `(eventId, status)` key" would drift the moment `SeatStatus` gains a fourth value and only one copy gets updated; one shared bean makes that drift impossible instead of just unlikely, the same reasoning `BaseEntity` gave for `equals`/`hashCode`.
- **`InvalidBookingStateTransitionException` mapped to `409 Conflict` before anything HTTP-facing could throw it (Day 16's cancel endpoint now does)** — added to `GlobalExceptionHandler` alongside the exception itself rather than waiting for Day 16's cancellation endpoint to need it, on the same "the mapping belongs with the exception, not with whichever caller happens to need it first" reasoning as every other handler in that class.
- **One shared `CommonErrorHandler`, wired in by replacing Spring Boot's auto-configured default `kafkaListenerContainerFactory` bean outright, rather than adding retry logic inside each listener method** — a `try/catch` with manual retry logic inside `onBookingConfirmed` would need to be written, tested, and kept in sync across three separate listener classes. Defining a bean with the exact name Boot's autoconfiguration would otherwise use means every listener that doesn't request a different factory picks up the identical policy for free, and there is exactly one place to change the backoff schedule later.
- **Exponential backoff, not fixed-interval retry** — a transient failure (a momentary DB connection blip, a broker leader election mid-request) is more likely resolved by a slightly longer pause each attempt than by hammering it at a constant interval, which looks a lot like whatever caused the failure to keep failing. Capped at 5s between attempts and 10s cumulative — long enough to survive a brief blip, short enough that a genuinely broken consumer still reaches the dead-letter topic in well under a minute instead of retrying indefinitely.
- **`InvalidBookingStateTransitionException` and `ResourceNotFoundException` registered as non-retryable exceptions** — both represent a permanent, logic-level problem that retrying cannot fix (a genuinely illegal state transition, or a `bookingId` on an event that doesn't exist in the database at all — not possible in normal operation, since `BookingConfirmedEventPublisher` only ever fires `AFTER_COMMIT`). Retrying either would just spend the full 10-second backoff budget before reaching the same dead-letter outcome anyway.
- **Two `.DLT` topics declared explicitly in `KafkaTopicConfig`, not left to broker auto-create** — the same "topology-as-code, visible in one place" reasoning as the two topics Day 11 declared, extended to the topics this project's own error-handling policy creates traffic on. Partition count matches each topic's source topic deliberately: `DeadLetterPublishingRecoverer`'s default destination resolver targets the same partition number on the DLT topic as the original record, so a DLT topic with fewer partitions would fail outright for messages from a higher-numbered source partition.
- **`EventChainIT` calls `BookingService.create()` directly rather than going through HTTP** — `BookingConcurrencyIT` already proves the controller-to-service wiring works under concurrent load; re-proving that here would add an HTTP hop with nothing new to verify. This test's actual subject is what happens *after* that call returns — the asynchronous chain across three independent Kafka consumers — so starting from the service call keeps the test's scope matched to what it's actually checking.
- **A dedicated `org.testcontainers:kafka` module dependency, unlike Redis's plain `GenericContainer`** — Redis (Day 10, `RedisCacheIT`) needed only a single exposed port and a trivial protocol, so a `GenericContainer` was the simpler choice there. Kafka's advertised-listener / broker-vs-client addressing is real complexity that a hand-rolled `GenericContainer` would mean reimplementing; testcontainers' purpose-built `KafkaContainer` exists specifically to hide that, which is the justification for reaching for a dedicated module here where Redis didn't need one.
- **`@SpyBean` on `NotificationListener`, not `@MockBean`** — two of `EventChainIT`'s three tests need the listener's real behavior (a log line) to keep running so that "did notification receive this event" is a meaningful assertion; only the retry/DLT test stubs it to throw. A `@MockBean` would have silently no-op'd every invocation across all three tests, making the first two tests unable to tell "notified" from "not notified."
- **Asserting "retried more than once," not an exact retry count, in the dead-letter test** — the exact number of attempts the 10-second exponential backoff budget produces depends on wall-clock timing that a loaded CI runner can legitimately skew. Asserting `atLeast(2)` invocations proves the retry policy actually ran before giving up, without making the test flaky over a detail (was it 3 attempts or 4?) that doesn't change whether the behavior being verified — retry, then dead-letter, instead of silent loss — is correct.
- **The booking owner comes from the authenticated principal, and `BookingRequest.userId` was deleted rather than validated** — checking that a client-supplied id matches the token would still make the field meaningless at best and a spoofing surface at worst; removing it means there is no request-body path to booking on someone else's behalf at all. The service methods take the id as an explicit parameter instead of reading `SecurityContextHolder`, which keeps `BookingServiceImpl` free of any Spring Security dependency and its unit tests free of security scaffolding.
- **Ownership is enforced in the service layer, not with URL rules or `@PreAuthorize`** — "is this *your* booking" depends on the specific row being loaded, which a static `authorizeHttpRequests` pattern can't express. Reusing Spring Security's own `AccessDeniedException` (not a custom exception) means one handler covers every current and future ownership check. Note the 403-vs-404 trade-off: a non-owner asking about a real booking id gets `403`, which confirms the id exists; returning `404` for both cases would hide that. Left as `403` for clarity — a deliberate, flagged simplification, not an oversight.
- **Registration is one endpoint with no roles system** — every authenticated user has the same capabilities, so anyone logged in can currently create or edit events, venues, and seats. A real deployment would gate those behind an admin role; the roadmap's ownership requirement is specifically about bookings, so a roles model is out of scope here and named rather than silently absent.
- **Wrong password and unknown email return the same `401 "Invalid email or password"`** — distinguishing them would let an attacker enumerate registered emails one login attempt at a time. Registration's duplicate-email check leans on the `UNIQUE` constraint (already mapped to `409`) instead of a pre-`SELECT`, since a check-then-insert would itself be a race.
- **Stateless HS256 JWT with no refresh tokens or revocation** — the smallest thing that satisfies "minimal JWT auth." The trade-off: a token is valid until it expires (default one hour) even if the user is deleted or changes password. The signing secret in `application.yml` is a local-dev placeholder that is committed to source control; any real deployment must override it via `JWT_SECRET`.
- **Cancelling races the payment listener, and that is a known, accepted gap** — `Booking` has no `@Version`, unlike `Seat`, so `cancel` and `PaymentProcessedListener` updating the same booking at the same instant is a possible lost update (last commit wins). Day 16 is about authentication and ownership; the gap is documented at the method rather than papered over with an untested fix.
- **`POST .../cancel`, not `DELETE`** — cancelling transitions the booking's status and keeps the row queryable; `DELETE` implies the resource disappears.
- **`JwtAuthenticationEntryPoint` exists because `@RestControllerAdvice` structurally cannot reach filter-chain rejections** — but no custom `AccessDeniedHandler` was added: this project has no role-based rules, and its only `403` (ownership) is thrown from service code, which the controller advice already handles.
- **JSON logs by default, plain text behind a `pretty` profile — not the other way around** — the default has to be what production consumes (a log shipper parses one JSON object per line, and `correlationId`/`bookingId` become filterable fields instead of text to regex); readability in a terminal is a developer convenience, so it's the opt-in. Logging to stdout rather than a file is the twelve-factor approach and means Day 18's `docker compose logs` works with zero extra configuration.
- **`logstash-logback-encoder`, not Spring Boot's built-in structured logging** — that feature (`logging.structured.format.console`) only exists from Spring Boot 3.4; this project is on 3.3.4, and the roadmap names the Logback-encoder route anyway. The library isn't managed by Boot's dependency BOM, so its version is pinned in `pom.xml` (8.x is the line built against Logback 1.5, which Boot 3.3 ships). Upgrading Boot later would let this dependency go away.
- **The correlation filter runs ahead of Spring Security, and a test proves it on a real `401`** — a request rejected by the security filter chain never reaches Spring MVC, so a correlation id assigned anywhere *after* security would leave exactly the requests you most need to debug — the rejected ones — with no id. `BookingConcurrencyIT` sends an unauthenticated request and asserts the caller's id comes back, which can only pass if the filter's position is right.
- **An inbound `X-Correlation-Id` is validated, not trusted** — accepting a caller's id is useful (a gateway can stamp one id across services), but it's copied into every log line and back into a response header. An unchecked value is a log-injection and header-injection vector, and an unbounded one bloats every line the request writes. Anything outside `[A-Za-z0-9._-]{1,64}` is discarded for a generated UUID; the request is still served.
- **The id is propagated over Kafka with interceptors, not a header added at each `send()` call site** — a `ProducerInterceptor` registered once in `application.yml` covers every send the app's producer makes, including ones no application code makes directly (Spring Kafka's dead-letter republish) and ones that don't exist yet. A call-site approach has to be remembered at every new `send`, and the first one someone forgets silently breaks the chain. It never overwrites an existing header, so a dead-lettered record keeps its *original* request's id.
- **The consumer-side interceptor is attached to both container factories explicitly** — the replaced default factory (`KafkaErrorHandlingConfig`) and the hand-built `PaymentEventsConsumerConfig` factory. A hand-built factory gets none of what Spring Boot's configurer applies to the default one, so forgetting the second would have silently dropped the id at the very last hop of the chain — the same trap Day 15 documented for the error handler. `EventChainIT` reads the id off a record on the second topic precisely to catch this.
- **The consumer interceptor overwrites the MDC on every record and also clears it after** — Kafka listener threads process record after record, and an id must never leak from one message to the next. Doing both (rather than trusting one of them) means a skipped cleanup on some failure path still can't hand a stale id to the next record. A record with no usable header gets a freshly generated id rather than an empty MDC, so its own log lines still correlate with *each other*.
- **Structured arguments (`kv`/`value`) rather than string-formatting the fields into the message** — `log.info("Booking {} created", value("bookingId", id), ..., kv("event", "booking.created"))` produces both a readable sentence and real fields. The `event` name is a stable constant (`LogEvents`) precisely because the message text is free to be reworded later while dashboards and alert rules keep working. A rule that comes with it: never use an MDC key (`userId`, `correlationId`) as an argument name — both become top-level JSON fields, and the result would be a duplicate key.
- **`booking.created` is logged after commit, in the Kafka publisher, not inside `BookingServiceImpl.create`** — logging "Booking created" from inside the `@Transactional` method would announce a booking that could still roll back. `BookingConfirmedEventPublisher` only runs at all if the transaction committed (Day 12's whole point), and it still runs on the request thread, so the line carries the request's `correlationId` and `userId` anyway.
- **The access log records the path, not the query string, and never a body** — query strings are where tokens, emails, and search terms end up; `/auth/login` and `/auth/register` bodies carry passwords and bearer tokens. Logs are routinely shipped somewhere less protected than the database, so what's never written can never leak from there. Health probes drop to `DEBUG` (an orchestrator polling every few seconds would otherwise be most of the log volume), and a 5xx is `WARN`, not `ERROR` — the real error with its stack trace was already logged where it happened, and this line is the request-level summary that shouldn't double-count the incident.
- **`userId` is put in the MDC by `JwtAuthenticationFilter` but cleared by `CorrelationIdFilter`** — the access-log line is written *after* the security filter has returned, and needs the id still present. Making the outermost filter the single owner of request-scoped MDC cleanup keeps that ordering dependency in one documented place and prevents thread reuse from ever attributing one request's user to the next.
- **The Spring banner is switched off** — with stdout now a stream of JSON events parsed line by line, the ASCII banner is the one thing Boot prints that isn't a log event, i.e. a guaranteed unparseable line at the top of every startup.
- **Correlation IDs, not distributed tracing** — the roadmap asks for a request id via filter + MDC, and that answers "everything this one request caused, across threads and Kafka hops." It doesn't give parent/child spans or per-hop timings; Micrometer Tracing / OpenTelemetry is the upgrade path if that's ever needed. The thread-local approach also doesn't follow work handed to another thread pool — nothing here does that today, and it's named in the Day 17 section as a limit rather than left to be discovered.
- **Multi-stage build: a Maven/JDK stage that's thrown away, a JRE-only stage that ships** — the compiler, Maven and the whole build cache never reach the runtime image. Smaller to pull and push (Day 20), and less in it to patch or attack. Alpine over a full Debian/Ubuntu base for the same reason; the one thing that costs is musl instead of glibc, which this dependency set (Postgres driver, Lettuce, Kafka client with no compression configured) doesn't care about.
- **Spring Boot layers, one `COPY` each, least-volatile first** — a jar is one opaque ~70 MB blob that changes on every commit; the same content split into layers means a code change rewrites a few hundred KB and everything beneath it stays cached, for builds now and for registry pushes later. The pom is copied and its dependencies resolved *before* `src/`, for the same reason one level up.
- **Tests are skipped in the image build, on purpose** — `-DskipTests` isn't cutting a corner: the `*IT` classes need Docker (Testcontainers) and there is no Docker daemon inside a build stage. Tests run in CI (Day 19), where a Docker daemon exists; an image build's job is to compile and package.
- **Non-root runtime user** — if the app process is ever compromised, the attacker lands as an unprivileged user in the container rather than as root.
- **`HEALTHCHECK` uses the liveness probe, not full health** — `/actuator/health/liveness` answers "is this process working?" and doesn't depend on Postgres or Redis; the full `/actuator/health` goes `DOWN` whenever a dependency is unreachable. "My database is restarting" shouldn't make the container declare *itself* dead. (Which dependencies the app should wait for at startup is a separate question, and is handled by `depends_on` health conditions in Part 2.)
- **Heap sized from the container limit, JVM exits on OOM** — `MaxRAMPercentage` follows whatever memory limit the container is given instead of assuming the whole host, leaving room for metaspace and thread stacks; `ExitOnOutOfMemoryError` turns a half-dead JVM that still answers health checks into a clean restart. The flags go through `JAVA_OPTS` and a shell-form `exec java ...` entrypoint rather than `JAVA_TOOL_OPTIONS`, because the JVM announces `JAVA_TOOL_OPTIONS` on stderr with a non-JSON line — exactly what Day 17's JSON-only log stream shouldn't contain. `exec` makes java PID 1 so `docker stop`'s SIGTERM reaches Spring Boot and it shuts down gracefully.
- **`DB_HOST` is a new environment variable, with the old value as its default** — "localhost" is correct on a developer machine and wrong inside a container, where Postgres is a different container reached by its compose service name. Redis and Kafka were already configurable by host; the database was the one that wasn't. The default means nothing changes for local runs.

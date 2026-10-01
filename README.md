# EventHub — Booking & Order Processing System

A production-grade event/ticket booking system demonstrating optimistic locking under concurrency, Redis caching, and event-driven order processing in Spring Boot. This is Project 3 of a 3-project backend portfolio (Core REST API → Auth & Authorization → **Production-grade Booking/Order System**).

**Status:** ✅ Day 18 complete — `docker compose up --build` runs the app, Postgres, Redis and Kafka with health-gated startup; configuration is documented in `.env.example`, and the full booking flow has a repeatable smoke test. CI, CD, and load testing follow (see [Roadmap](#roadmap) below).

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
- PowerShell 5.1+ (only for the full-stack smoke test)

## Running locally

**Everything in containers** (the app too — the Docker image is built from the `Dockerfile`):

```bash
cp .env.example .env       # optional: review/customize local values first
docker compose up --build
curl http://localhost:8080/actuator/health/liveness
```

On Windows PowerShell, use `Copy-Item .env.example .env` instead of `cp`.

**Or: dependencies in containers, app from your IDE/terminal** (faster edit-run cycle):

1. Start Postgres + Redis + Kafka only:
   ```bash
   docker compose up -d postgres redis kafka
   ```
2. Run the app (stop the `app` container first if it's running — both want port 8080):
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

Copy `.env.example` to `.env` to customize the Compose stack. The application and Compose settings are overridable via environment variables, with sane local defaults baked in:

| Variable       | Default          | Notes                                   |
|-----------------|-------------------|------------------------------------------|
| `DB_PORT`         | `5433`             | Matches the host port in `docker-compose.yml` |
| `DB_NAME`           | `eventhub_db`        |                                            |
| `DB_USER`             | `eventhub_user`        |                                            |
| `DB_PASSWORD`           | `eventhub_pass`          | Local dev only — never used as-is in a real deployment |
| `REDIS_HOST`              | `localhost`                |                                            |
| `REDIS_PORT`                | `6380`                        | Matches the host port in `docker-compose.yml` |
| `KAFKA_BOOTSTRAP_SERVERS`     | `localhost:9094`                | Host-side address: the published port `9094` (not Kafka's usual `9092`, to avoid clashing with a local broker). Inside compose the app uses `kafka:29092` instead |
| `KAFKA_HOST_PORT`             | `9094`                          | Compose host port for Kafka's external listener |
| `SERVER_PORT`             | `8080`                    |                                            |
| `JWT_SECRET`                | a local-dev placeholder      | **Must** be overridden in any real deployment — the default is committed to source control. Needs 32+ bytes for HS256 |
| `JWT_EXPIRATION_MS`           | `3600000` (1 hour)             | Token lifetime                              |
| `PAYMENT_MOCK_DECLINE_THRESHOLD` | `1000.00`                    | Mock payments at or above this total are declined |
| `DB_HOST`                      | `localhost`                      | Postgres host. Added Day 18: inside a container this is the Postgres service's name, not `localhost` |
| `SPRING_PROFILES_ACTIVE`        | *(unset)*                        | Unset = JSON logs. `pretty` = plain-text, human-readable console logs (Day 17) |
| `APP_IMAGE`                     | `eventhub-booking-system:dev`    | Compose image name/tag |
| `APP_MEMORY_LIMIT`              | `1g`                             | Compose memory limit used by container-aware JVM sizing |

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

**A note on the historical walkthrough below:** the numbered Day 1–15 walkthrough records how the system was built and predates authentication, so its mutation commands are not a current smoke test. Use the [Authenticated flow](#authenticated-flow-register-book-cancel) for runnable register → login → book → cancel commands against this version.

## Usage examples

**Full happy path — register → venue → event → seat → booking:**

```bash
curl -X POST http://localhost:8080/api/v1/auth/register -H "Content-Type: application/json" \
  -d '{"fullName":"Ahmed Test","email":"ahmed@example.com","password":"correct-horse-battery"}'
TOKEN=<paste-the-token-from-the-response>

curl -X POST http://localhost:8080/api/v1/venues -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"Cairo Arena","city":"Cairo","address":"Nasr City","capacity":5000}'

curl -X POST http://localhost:8080/api/v1/events -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"venueId":1,"name":"Launch Night","description":"Opening event","category":"CONCERT","eventDate":"2026-12-01T19:00:00Z"}'

curl -X POST http://localhost:8080/api/v1/events/1/seats -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"seatNumber":"A1","section":"Floor","price":50.00}'

curl -X POST http://localhost:8080/api/v1/bookings -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"seatIds":[1]}'
```

**Trying to book the same seat again returns 409, not a 500** (and Day 7's `BookingConcurrencyIT` proves this holds even when the requests genuinely race, not just when they're sequential like this):

```bash
curl -i -X POST http://localhost:8080/api/v1/bookings -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"seatIds":[1]}'
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
  -H "Authorization: Bearer $TOKEN" \
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
docker-compose.yml           # Day 18 - the full stack: app + Postgres + Redis + Kafka, health-gated startup
.env.example                 # Day 18 - safe, committed template for local Compose configuration
scripts/smoke-test.ps1       # Day 18 - end-to-end register -> book -> pay -> cancel verification
LICENSE                      # MIT
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
    ├── V4__add_user_password.sql         # Day 16 - users.password_hash
    ├── V5__booking_optimistic_locking.sql # prevents cancel/payment lost updates
    └── V6__case_insensitive_user_email.sql # enforces normalized email identity

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

Day 18 is built in three parts. **All three parts are complete.**

- [x] **Part 1 — the image.** Multi-stage `Dockerfile` + `.dockerignore`, and the one config change the app needed to run in a container.
- [x] **Part 2 — the stack.** The app joins `docker-compose.yml`, with health-gated startup order and Kafka reachable from both the host and other containers.
- [x] **Part 3 — polish.** `.env.example`, a full-stack smoke test, docs.

### Part 1: the image

- **`Dockerfile`, two stages.** *Build*: `maven:3.9-eclipse-temurin-21` compiles and packages the app, then splits the jar into Spring Boot's layers. *Runtime*: `eclipse-temurin:21-jre-alpine` — JRE only, no Maven, no compiler — receives just those layers. Only the runtime stage is the shipped image.
- **Layered, so rebuilds are fast.** Dependencies, loader, snapshot dependencies and application code are separate image layers, ordered least to most volatile. A code-only change rebuilds a layer of a few hundred KB and reuses the cached dependency layer; the pom is copied and resolved *before* the source for the same reason.
- **Tests are skipped in the image build** (`-DskipTests`): the `*IT` classes need Docker (Testcontainers) and there's no Docker daemon inside a build stage. Tests run in CI (Day 19).
- **Runs as a non-root user** (`eventhub`, uid 1001).
- **`HEALTHCHECK`** against `/actuator/health/liveness` (public, no token) using Alpine's built-in `wget`. Liveness, not full health: the full endpoint goes `DOWN` whenever Postgres or Redis is unreachable, and "my database is restarting" shouldn't make the container declare *itself* dead.
- **Container-aware JVM** — heap sized as a percentage of the container's memory limit, and the JVM exits on `OutOfMemoryError` so it gets restarted instead of lingering half-dead. Flags go through `JAVA_OPTS` and an `exec java` entrypoint (not `JAVA_TOOL_OPTIONS`, which the JVM announces on stderr with a non-JSON line); `exec` makes java PID 1 so `docker stop` triggers a graceful Spring Boot shutdown.
- **`.dockerignore`** keeps `target/`, `.git`, IDE files and any `.env` out of the build context.
- **`DB_HOST`** added to `application.yml`. The database host used to be hardcoded to `localhost`; inside a container that is the app's own container, not Postgres. The default is unchanged, so running locally behaves exactly as before.

### Part 2: the stack

- **`app` service in `docker-compose.yml`** — built from the `Dockerfile`, published on `8080`, wired to the other services by their compose service names (`postgres:5432`, `redis:6379`, `kafka:29092`). Inside the compose network containers use each other's *container* ports; the host-mapped ports (`5433`, `6380`, `9094`) are only for things running on your machine.
- **Health-gated startup** — `depends_on` with `condition: service_healthy` on Postgres, Redis and Kafka. A plain `depends_on` only waits for a container to *exist*; these take seconds more to accept connections, and without the gate Flyway's first migration and Kafka's topic creation would race them.
- **Kafka has two listeners now.** Kafka's protocol makes the broker tell every client which address to use for later requests, and the right address depends on where the client runs: `kafka:29092` (`INTERNAL`) for other containers, `localhost:9094` (`EXTERNAL`, the published port) for the host. One advertised address can't serve both — `localhost` inside the app container is the container itself. This is what lets `docker compose up` *and* `mvn spring-boot:run` work against the same broker. The broker healthcheck now probes the internal listener.
- **Memory limit (`1g`)** on the app container — it's what the JVM's `MaxRAMPercentage` is a percentage *of*; without a limit the heap would be sized from the host's RAM.
- **Log rotation** (`json-file`, 10 MB x 3) — Day 17 made stdout a JSON log stream, and Docker's default driver keeps all of it forever.
- **`JWT_SECRET` is overridable** (`JWT_SECRET=... docker compose up`). The fallback is the same local-dev-only placeholder `application.yml` ships with — fine for a local demo, never for a real deployment.

### Part 3: release polish

- **`.env.example`** documents every Compose-level setting without committing a real secret. Copy it to the ignored `.env` file before changing ports, credentials, image tags, memory, token lifetime, or the mock payment threshold.
- **`scripts/smoke-test.ps1`** exercises the actual running stack: readiness, registration, authenticated venue/event/seat creation, asynchronous Kafka payment confirmation, booked-seat state, cancellation, seat release, and correlation-ID propagation. Each run creates uniquely named data, so it is repeatable against a persistent local database.
- **Compose defaults remain zero-config.** The environment template is optional; `docker compose up --build` still works with the documented defaults.

### Try it

```bash
# Everything: Postgres + Redis + Kafka + the app. First run builds the image.
docker compose up -d --build

# The app container should become "healthy"
docker compose ps
curl http://localhost:8080/actuator/health/liveness     # {"status":"UP"}
curl http://localhost:8080/actuator/health/readiness    # {"status":"UP"}

# Windows PowerShell: run the full booking/payment/cancellation smoke test
.\scripts\smoke-test.ps1

# The app's JSON logs, including the Flyway migrations and Kafka startup
docker compose logs -f app

# The full flow: see "Authenticated flow: register, book, cancel" above.
# Watch the booking -> payment -> notification chain share one correlation id:
docker compose logs app | grep '"correlationId":"<id from the X-Correlation-Id response header>"'

# Stop everything (add -v to also delete the Postgres/Redis/Kafka data volumes)
docker compose down
```

Things to know:

- **Port clash:** the `app` container publishes `8080`. If you also run `mvn spring-boot:run` at the same time, one of them can't bind it — stop one, or set a different `SERVER_PORT` locally.
- **Infra only:** `docker compose up -d postgres redis kafka` starts just the dependencies, for the original run-the-app-from-your-IDE workflow; the host-mapped ports are unchanged.
- **Existing Kafka volume:** if you started the old single-listener Kafka earlier, its data volume is reused and works with the new listeners. If anything looks stuck, `docker compose down -v` gives a clean slate.

### Current design trade-offs

- **Kafka instead of the roadmap's default RabbitMQ suggestion:** keyed messages give per-booking ordering, while independent consumer groups and explicit retry/DLT behavior demonstrate the event-stream semantics this project needs. The extra broker configuration is intentional, not an accidental queue replacement.
- **After-commit publishing is not a transactional outbox:** consumers never see a rolled-back booking, but there is still a small crash window between the database commit and Kafka acknowledgement. A production version would write an outbox row in the booking transaction and publish/retry it separately; the current roadmap treats that extra infrastructure as a stretch goal.

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
- [x] Day 18 — multi-stage Dockerfile + full docker-compose stack (app + Postgres + Redis + Kafka), environment template, and end-to-end smoke test
- [ ] Day 19 — GitHub Actions CI (test + build on push)
- [ ] Day 20 — GitHub Actions CD (build & push image)
- [ ] Day 21 — load test under concurrency, fix findings
- [ ] Day 22 — architecture diagram + design decisions section
- [ ] Day 23 — OpenAPI/Swagger polish + demo seed data
- [ ] Day 24 — final polish, `v1.0` tag

## License

Released under the [MIT License](LICENSE).

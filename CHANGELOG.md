# Changelog

All notable changes to EventHub are documented here.

## [1.0.0] - 2026-10-02

### Highlights

- Concurrency-safe multi-seat booking with optimistic locking and clean `409 Conflict` responses.
- Redis cache-aside reads with per-cache TTLs, write-side invalidation, failure degradation, and hit metrics.
- Kafka booking → payment → notification flow with correlation propagation, retries, and dead-letter topics.
- Stateless JWT authentication, booking ownership checks, validation, and a consistent error contract.
- Multi-stage non-root container image and a health-gated Docker Compose stack.
- GitHub Actions CI plus tested-image publication to GitHub Container Registry.
- Real-stack smoke and concurrent load harnesses.
- Editable architecture diagrams, explicit design trade-offs, Swagger UI, and idempotent demo seed data.

### Verification

- `mvn verify`: 95 unit/context tests and 11 Testcontainers integration tests.
- `scripts/smoke-test.ps1`: register → create catalog → book → Kafka payment → cancel → seat release.
- `scripts/load-test.ps1`: one winner per seat under contention and measured cache hits under concurrent reads.

### Known production follow-ups

- Replace after-commit Kafka publication with a transactional outbox.
- Run Kafka as a secured replicated cluster and add DLT monitoring/replay operations.
- Replace the deterministic payment mock with an idempotent gateway adapter.
- Add role-based administration, token refresh/revocation, and managed secret rotation.

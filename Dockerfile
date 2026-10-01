# syntax=docker/dockerfile:1
#
# Day 18 (Part 1): multi-stage build.
#
#   build   - full JDK + Maven; compiles the app, then splits the fat jar
#             into Spring Boot's layers. Never shipped.
#   runtime - JRE only, non-root, layers copied in from the build stage.
#             This is the image you actually run and push (Day 20).
#
# Build:  docker build -t eventhub-booking-system:dev .

# ---------------------------------------------------------------------------
# Stage 1: build
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Dependencies first, source second. pom.xml changes rarely; source changes
# on every commit. Copying only the pom and resolving dependencies in its
# own layer means an ordinary code change reuses the cached dependency
# layer instead of re-downloading the whole dependency tree every build.
# The cache mount keeps ~/.m2 between builds on this machine as well, so
# even a pom change only fetches what's new.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline

COPY src ./src

# -DskipTests: the unit tests run in CI (Day 19), and the *IT classes need
# Docker themselves (Testcontainers) - there is no Docker daemon inside a
# build stage. An image build compiles and packages; it isn't the test run.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package -DskipTests

# Split the fat jar into Spring Boot's layers (dependencies /
# spring-boot-loader / snapshot-dependencies / application), ordered from
# least to most likely to change. Extracted here, copied layer by layer in
# the runtime stage below.
RUN java -Djarmode=layertools -jar target/eventhub-booking-system.jar extract --destination target/extracted

# ---------------------------------------------------------------------------
# Stage 2: runtime
# ---------------------------------------------------------------------------
# JRE (no compiler, no Maven) on Alpine: the final image is a fraction of
# the size of the build stage, and has far less in it to patch or attack.
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL org.opencontainers.image.title="eventhub-booking-system" \
      org.opencontainers.image.description="EventHub - ticket booking and order processing API"

# Never run as root inside the container: if the app is ever compromised,
# the attacker gets an unprivileged user, not root in the container.
RUN addgroup -S -g 1001 eventhub && adduser -S -u 1001 -G eventhub eventhub

WORKDIR /app

# One COPY per layer, least volatile first. A code-only change invalidates
# just the last layer (a few hundred KB); the ~60-80 MB of dependencies
# underneath stays cached, so rebuilding AND re-pushing (Day 20) is fast.
COPY --from=build --chown=eventhub:eventhub /workspace/target/extracted/dependencies/ ./
COPY --from=build --chown=eventhub:eventhub /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=eventhub:eventhub /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=eventhub:eventhub /workspace/target/extracted/application/ ./

USER eventhub

EXPOSE 8080

# Size the heap from the container's memory limit, not the host's: with
# MaxRAMPercentage the JVM uses a share of whatever limit the container is
# given (compose / Kubernetes), leaving headroom for metaspace, thread
# stacks and native memory. ExitOnOutOfMemoryError makes an OOM kill the
# process so the orchestrator restarts it, instead of leaving a half-dead
# JVM that still answers health checks.
# Passed through JAVA_OPTS (read by the shell entrypoint below) rather than
# JAVA_TOOL_OPTIONS: the JVM prints a "Picked up JAVA_TOOL_OPTIONS" line to
# stderr on every start, which is a non-JSON line in a log stream that Day
# 17 made JSON-only.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Liveness, not full health: /actuator/health/liveness reports only whether
# the app process itself is working. The full /actuator/health also goes DOWN
# when Postgres or Redis is unreachable - and "my dependency is down" is not
# a reason to declare the container itself dead. /actuator/health/** is
# public (SecurityConfig), so no token is needed. busybox wget ships with
# Alpine, so no extra package is installed just for this.
# ${SERVER_PORT:-8080} so the check follows the port if it's overridden.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO /dev/null "http://localhost:${SERVER_PORT:-8080}/actuator/health/liveness" || exit 1

# `exec` makes java PID 1, so `docker stop`'s SIGTERM reaches the JVM and
# Spring Boot shuts down gracefully (finishing in-flight requests, closing
# the Kafka consumers and connection pool) rather than being killed after
# the stop timeout. The shell form is only here to expand $JAVA_OPTS.
# JarLauncher runs the extracted layers directly - no fat-jar unpacking at
# startup, which also starts faster than `java -jar app.jar`.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

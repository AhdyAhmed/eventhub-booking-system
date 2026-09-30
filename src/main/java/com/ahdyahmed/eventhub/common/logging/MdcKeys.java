package com.ahdyahmed.eventhub.common.logging;

/**
 * The names of the two request-scoped values this project puts in the SLF4J
 * {@code MDC} (Mapped Diagnostic Context). {@code LogstashEncoder} copies
 * every MDC entry into the JSON log line as a top-level field, so anything
 * added here shows up on <em>every</em> log line emitted while it's set —
 * without a single {@code log.info(...)} call site having to pass it.
 *
 * <p>That last part is why these are constants in one place rather than
 * string literals scattered across the filters and Kafka interceptors that
 * read and write them: a typo in one place would silently produce a log
 * field nothing else ever matches.</p>
 *
 * <p><strong>Never reuse these names as structured-argument keys</strong>
 * ({@code kv("userId", ...)}): an MDC entry and a structured argument with
 * the same name both become top-level JSON fields, and the result would be
 * an object with a duplicate key.</p>
 */
public final class MdcKeys {

    /** One id per inbound HTTP request; carried across Kafka hops in a message header. */
    public static final String CORRELATION_ID = "correlationId";

    /**
     * The authenticated caller's id. Only present on HTTP request threads,
     * and only once {@code JwtAuthenticationFilter} has actually
     * authenticated the request.
     */
    public static final String USER_ID = "userId";

    private MdcKeys() {
    }
}

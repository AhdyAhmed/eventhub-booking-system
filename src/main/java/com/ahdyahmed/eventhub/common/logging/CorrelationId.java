package com.ahdyahmed.eventhub.common.logging;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The correlation-id contract shared by everything that touches it: the HTTP
 * filter, both Kafka interceptors, and {@code ErrorResponse}.
 *
 * <p><strong>Why an inbound id is validated instead of trusted as-is:</strong>
 * accepting a caller-supplied {@code X-Correlation-Id} is genuinely useful
 * (an API gateway or a frontend can stamp one id across every service a
 * user action touches), but the value goes straight into every log line and
 * back out in a response header. An unchecked value is a log-injection and
 * header-injection vector (a newline inside it could forge a fake log line
 * or split a response header), and an unbounded one is a way to bloat every
 * line this request ever logs. Anything outside {@code [A-Za-z0-9._-]{1,64}}
 * is discarded and replaced with a freshly generated id — the request is
 * still served, it just doesn't get to choose a hostile id.</p>
 */
public final class CorrelationId {

    /** HTTP request/response header and Kafka record header carrying the id. */
    public static final String HEADER = "X-Correlation-Id";

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private CorrelationId() {
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    /** Returns {@code candidate} if it's a safe id, otherwise a newly generated one. */
    public static String sanitizeOrGenerate(String candidate) {
        if (candidate != null && SAFE.matcher(candidate).matches()) {
            return candidate;
        }
        return generate();
    }
}

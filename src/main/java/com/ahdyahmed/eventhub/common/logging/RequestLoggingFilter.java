package com.ahdyahmed.eventhub.common.logging;

import static com.ahdyahmed.eventhub.common.logging.LogEvents.HTTP_REQUEST_COMPLETED;
import static net.logstash.logback.argument.StructuredArguments.kv;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One access-log line per finished request: method, path, status, and how
 * long it took. The {@code correlationId} (and, for authenticated requests,
 * {@code userId}) come along for free from the MDC — see {@link
 * CorrelationIdFilter} and {@code JwtAuthenticationFilter}.
 *
 * <p><strong>Runs one step inside {@link CorrelationIdFilter}</strong>
 * ({@code +20} vs. {@code +10}) so the correlation id is already in the MDC
 * when the line is written. It logs from a {@code finally}, after the chain
 * has unwound, so the status and duration are real values — and the
 * {@code userId} MDC entry set deep inside the security chain is still
 * present, since {@code CorrelationIdFilter} (which clears it) is further
 * out.</p>
 *
 * <p><strong>Deliberate choices:</strong></p>
 * <ul>
 *   <li>The path is logged, the <em>query string is not</em>. Query strings
 *   are where tokens, emails, and search terms end up; an access log that
 *   records them is a data-leak waiting for someone to ship the logs
 *   somewhere less protected than the database.</li>
 *   <li>Request and response <em>bodies</em> are never logged — they carry
 *   passwords ({@code /auth/login}) and bearer tokens
 *   ({@code /auth/register}'s response).</li>
 *   <li>{@code /actuator/health} probes are logged at {@code DEBUG}, not
 *   {@code INFO}: an orchestrator polling every few seconds would otherwise
 *   make up most of the log volume while telling nobody anything.</li>
 *   <li>A 5xx is {@code WARN}. It is not {@code ERROR}: the real error, with
 *   its stack trace, was already logged at the point it happened
 *   ({@code GlobalExceptionHandler}); this line is the request-level
 *   summary and shouldn't double-count the incident.</li>
 *   <li>If the chain throws, the status is recorded as 500 and the exception
 *   propagates unchanged — this filter observes failures, it doesn't swallow
 *   them.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String HEALTH_PATH_PREFIX = "/actuator/health";

    // {} placeholders (via kv, which renders as name=value in the message)
    // keep the plain-text "pretty" profile readable; the same arguments
    // become individual JSON fields under the default JSON profile.
    private static final String MESSAGE = "HTTP request completed {} {} {} {}";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        try {
            chain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            logCompleted(request, status, durationMs);
        }
    }

    private void logCompleted(HttpServletRequest request, int status, long durationMs) {
        String path = request.getRequestURI();
        Object[] args = {
                kv("method", request.getMethod()),
                kv("path", path),
                kv("status", status),
                kv("durationMs", durationMs),
                // No placeholder in MESSAGE: goes to the JSON output only.
                kv("event", HTTP_REQUEST_COMPLETED)
        };

        if (status >= 500) {
            log.warn(MESSAGE, args);
        } else if (path.startsWith(HEALTH_PATH_PREFIX)) {
            log.debug(MESSAGE, args);
        } else {
            log.info(MESSAGE, args);
        }
    }
}

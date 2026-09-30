package com.ahdyahmed.eventhub.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every HTTP request a correlation id, makes it available to every
 * log line the request produces (via the {@code MDC}), and hands it back to
 * the caller in the {@code X-Correlation-Id} response header — the id a
 * client quotes in a bug report, and the one you grep for to see everything
 * that request did.
 *
 * <p><strong>Why the order is {@code HIGHEST_PRECEDENCE + 10}:</strong>
 * Spring Security's filter chain runs at order {@code -100}. This filter has
 * to sit <em>ahead</em> of it, not merely somewhere in the servlet filters,
 * because a request rejected by Spring Security (a missing or invalid token
 * → 401 from {@code JwtAuthenticationEntryPoint}) never reaches Spring MVC
 * at all. A correlation filter placed after security would leave exactly the
 * requests you most often need to debug — the rejected ones — with no id.
 * {@code BookingConcurrencyIT} asserts this on a real 401.</p>
 *
 * <p><strong>Why the response header is set before the chain runs</strong>,
 * not after: once a response is committed (a large body flushed, an error
 * sent), headers can no longer be added. Setting it up front guarantees the
 * header is present on every response, including error responses.</p>
 *
 * <p><strong>Cleanup is the whole point of the {@code finally}.</strong>
 * Servlet containers reuse threads. An MDC entry left behind by one request
 * would be silently attributed to the next unrelated request that happens to
 * land on the same thread — a correlation id that lies is worse than none.
 * This filter is the outermost owner of request-scoped MDC state, so it also
 * removes {@link MdcKeys#USER_ID}, which {@code JwtAuthenticationFilter}
 * (further inside the chain) sets but deliberately does not clear itself:
 * {@code RequestLoggingFilter}'s access-log line needs the user id still
 * present when control unwinds back out through it.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = CorrelationId.sanitizeOrGenerate(request.getHeader(CorrelationId.HEADER));

        MDC.put(MdcKeys.CORRELATION_ID, correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MdcKeys.CORRELATION_ID);
            MDC.remove(MdcKeys.USER_ID);
        }
    }
}

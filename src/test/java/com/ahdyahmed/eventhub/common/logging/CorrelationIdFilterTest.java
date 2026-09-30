package com.ahdyahmed.eventhub.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Pure unit test — no Spring context, no Docker. The filter has no
 * collaborators, so it's driven directly with Spring's mock servlet objects
 * and a lambda {@code FilterChain} that records what the MDC looked like
 * <em>while the request was being handled</em> (the only moment that matters
 * for a thread-local).
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void noInboundHeader_generatesAnId_exposesItInMdc_andEchoesItOnTheResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seenInsideChain.set(MDC.get(MdcKeys.CORRELATION_ID)));

        String echoed = response.getHeader(CorrelationId.HEADER);
        assertThat(echoed).isNotBlank();
        assertThat(seenInsideChain.get()).isEqualTo(echoed);
        // Generated ids are UUIDs - parses or throws.
        assertThat(UUID.fromString(echoed)).isNotNull();
    }

    @Test
    void safeInboundHeader_isReusedNotReplaced() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
        request.addHeader(CorrelationId.HEADER, "gateway-req_42.a");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seenInsideChain.set(MDC.get(MdcKeys.CORRELATION_ID)));

        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("gateway-req_42.a");
        assertThat(seenInsideChain.get()).isEqualTo("gateway-req_42.a");
    }

    @Test
    void unsafeInboundHeader_isDiscardedAndReplacedWithAGeneratedId() throws Exception {
        // Each of these would be a log-injection / header-injection vector
        // or an unbounded per-line payload if trusted as-is.
        List<String> hostile = List.of(
                "line1\nX-Injected: yes",
                "has spaces",
                "semi;colon",
                "<script>",
                "x".repeat(65),
                "");

        for (String value : hostile) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
            request.addHeader(CorrelationId.HEADER, value);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> { });

            String echoed = response.getHeader(CorrelationId.HEADER);
            assertThat(echoed).as("replacement for hostile value %s", value).isNotEqualTo(value);
            assertThat(UUID.fromString(echoed)).isNotNull();
        }
    }

    @Test
    void mdcIsClearedAfterTheRequest_soAReusedThreadNeverInheritsTheLastRequestsId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            // Simulates JwtAuthenticationFilter, deeper in the chain.
            MDC.put(MdcKeys.USER_ID, "7");
        });

        assertThat(MDC.get(MdcKeys.CORRELATION_ID)).isNull();
        // userId is set by JwtAuthenticationFilter but owned (cleared) here.
        assertThat(MDC.get(MdcKeys.USER_ID)).isNull();
    }

    @Test
    void mdcIsClearedEvenWhenTheChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            throw new ServletException("boom");
        })).isInstanceOf(ServletException.class);

        assertThat(MDC.get(MdcKeys.CORRELATION_ID)).isNull();
    }

    @Test
    void headerIsPresentOnTheResponseEvenIfTheChainFails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw new ServletException("boom");
        })).isInstanceOf(ServletException.class);

        assertThat(response.getHeader(CorrelationId.HEADER)).isNotBlank();
    }
}

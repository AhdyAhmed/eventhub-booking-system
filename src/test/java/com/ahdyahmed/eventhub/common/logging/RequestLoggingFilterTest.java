package com.ahdyahmed.eventhub.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Captures what the filter actually logs by attaching a Logback {@link
 * ListAppender} to its logger — asserting on real log events (level and
 * rendered message), not on whether some method was called. The logger's
 * level is pinned to DEBUG for the test so the health-probe case (logged at
 * DEBUG) is observable regardless of what a stray Logback config on the
 * classpath would otherwise decide, and restored afterward.
 */
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final Logger filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);

    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
        originalLevel = filterLogger.getLevel();
        filterLogger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        filterLogger.detachAppender(appender);
        filterLogger.setLevel(originalLevel);
    }

    @Test
    void ordinaryRequest_logsOneInfoLineWithMethodPathStatusAndDuration() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/bookings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(201));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .contains("method=POST")
                .contains("path=/api/v1/bookings")
                .contains("status=201")
                .contains("durationMs=");
        // The machine-readable event name rides along as a structured
        // argument (JSON field) without a placeholder in the message.
        assertThat(event.getArgumentArray())
                .anyMatch(arg -> "event=http.request.completed".equals(String.valueOf(arg)));
    }

    @Test
    void healthProbe_isLoggedAtDebug_notInfo() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health/liveness");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.DEBUG);
    }

    @Test
    void serverError_isLoggedAtWarn() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> ((HttpServletResponse) res).setStatus(500));

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("status=500");
    }

    @Test
    void clientError_isStillInfo_notWarn() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/bookings/1");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> ((HttpServletResponse) res).setStatus(401));

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("status=401");
    }

    @Test
    void chainThrows_isStillLoggedAs500_andTheExceptionPropagatesUnchanged() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            throw new ServletException("boom");
        })).isInstanceOf(ServletException.class).hasMessage("boom");

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("status=500");
    }

    @Test
    void queryString_isNeverLogged() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
        request.setQueryString("token=super-secret&email=a@b.com");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage())
                .doesNotContain("super-secret")
                .doesNotContain("a@b.com")
                .contains("path=/api/v1/events");
    }
}

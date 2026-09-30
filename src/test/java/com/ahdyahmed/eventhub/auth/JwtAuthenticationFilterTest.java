package com.ahdyahmed.eventhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ahdyahmed.eventhub.common.logging.MdcKeys;
import com.ahdyahmed.eventhub.user.User;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Day 17's slice of {@code JwtAuthenticationFilter}: whether an authenticated
 * request ends up with {@code userId} in the MDC (and so on every log line it
 * writes). A real {@link JwtService} — it has no dependencies worth mocking,
 * same choice as {@code JwtServiceTest} — and a mocked user lookup.
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET =
            "test-only-signing-secret-at-least-32-bytes-long-for-hs256-xxxxx";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60_000L));
    private final EventHubUserDetailsService userDetailsService = mock(EventHubUserDetailsService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);

    @AfterEach
    void cleanUp() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    private UserPrincipal principal(long id, String email) {
        User user = User.builder().id(id).fullName("Test User").email(email)
                .passwordHash("irrelevant-for-this-test").build();
        return new UserPrincipal(user);
    }

    @Test
    void validToken_putsUserIdInMdcForTheRestOfTheRequest() throws Exception {
        UserPrincipal principal = principal(42L, "sara@example.com");
        when(userDetailsService.loadUserByUsername("sara@example.com")).thenReturn(principal);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/bookings/1");
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(principal));
        AtomicReference<String> seenInsideChain = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> seenInsideChain.set(MDC.get(MdcKeys.USER_ID)));

        assertThat(seenInsideChain.get()).isEqualTo("42");
    }

    @Test
    void validToken_leavesUserIdInPlaceAfterReturning_becauseCorrelationIdFilterOwnsTheCleanup() throws Exception {
        UserPrincipal principal = principal(42L, "sara@example.com");
        when(userDetailsService.loadUserByUsername("sara@example.com")).thenReturn(principal);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/bookings/1");
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(principal));

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        // Deliberate (see the filter's own comment): RequestLoggingFilter's
        // access-log line is written after this filter returns and needs it.
        assertThat(MDC.get(MdcKeys.USER_ID)).isEqualTo("42");
    }

    @Test
    void noAuthorizationHeader_setsNoUserId_andStillContinuesTheChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
        AtomicBoolean chainContinued = new AtomicBoolean(false);
        AtomicReference<String> seenInsideChain = new AtomicReference<>("sentinel");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            chainContinued.set(true);
            seenInsideChain.set(MDC.get(MdcKeys.USER_ID));
        });

        assertThat(chainContinued).isTrue();
        assertThat(seenInsideChain.get()).isNull();
    }

    @Test
    void garbageToken_setsNoUserId_andStillContinuesTheChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
        request.addHeader("Authorization", "Bearer not-a-real-jwt");
        AtomicBoolean chainContinued = new AtomicBoolean(false);
        AtomicReference<String> seenInsideChain = new AtomicReference<>("sentinel");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            chainContinued.set(true);
            seenInsideChain.set(MDC.get(MdcKeys.USER_ID));
        });

        assertThat(chainContinued).isTrue();
        assertThat(seenInsideChain.get()).isNull();
    }
}

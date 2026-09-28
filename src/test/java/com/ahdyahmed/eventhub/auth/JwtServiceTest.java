package com.ahdyahmed.eventhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ahdyahmed.eventhub.user.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * A 64-character literal secret, not the shorter placeholder from {@code
 * application.yml} — HS256 needs at least 256 bits (32 bytes), and this
 * test shouldn't depend on the exact length of whatever the real config
 * file happens to contain today.
 */
class JwtServiceTest {

    private static final String SECRET =
            "test-only-signing-secret-at-least-32-bytes-long-for-hs256-xxxxx";

    private UserPrincipal principal(long id, String email) {
        User user = User.builder().id(id).fullName("Test User").email(email)
                .passwordHash("irrelevant-for-this-test").build();
        return new UserPrincipal(user);
    }

    @Test
    void generateToken_thenExtractEmail_roundTrips() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60_000L));
        UserPrincipal principal = principal(1L, "jwt-test@example.com");

        String token = jwtService.generateToken(principal);

        assertThat(jwtService.extractEmail(token)).isEqualTo("jwt-test@example.com");
    }

    @Test
    void isValid_forTheUserItWasIssuedTo_isTrue() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60_000L));
        UserPrincipal principal = principal(1L, "jwt-test@example.com");

        String token = jwtService.generateToken(principal);

        assertThat(jwtService.isValid(token, principal)).isTrue();
    }

    @Test
    void isValid_forADifferentUser_isFalse() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60_000L));
        String token = jwtService.generateToken(principal(1L, "owner@example.com"));

        UserDetails someoneElse = principal(2L, "someone-else@example.com");

        assertThat(jwtService.isValid(token, someoneElse)).isFalse();
    }

    @Test
    void expiredToken_isNotValid() throws InterruptedException {
        // 1ms expiration, not 0 - some clock implementations treat a
        // zero-duration token as "expires exactly now," which can race
        // against the isExpired() check depending on how many nanoseconds
        // elapsed between signing and checking. Sleeping past even a
        // trivially short real expiration removes that ambiguity entirely.
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, 1L));
        UserPrincipal principal = principal(1L, "jwt-test@example.com");
        String token = jwtService.generateToken(principal);

        Thread.sleep(50);

        assertThatThrownBy(() -> jwtService.isValid(token, principal))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithADifferentSecret_isRejected() {
        JwtService issuer = new JwtService(new JwtProperties(SECRET, 60_000L));
        String token = issuer.generateToken(principal(1L, "jwt-test@example.com"));

        JwtService verifier = new JwtService(new JwtProperties(
                "a-completely-different-signing-secret-also-32-bytes-plus-yyyyy", 60_000L));

        assertThatThrownBy(() -> verifier.extractEmail(token))
                .isInstanceOf(JwtException.class);
    }
}

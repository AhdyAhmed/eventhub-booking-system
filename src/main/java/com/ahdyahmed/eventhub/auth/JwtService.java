package com.ahdyahmed.eventhub.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * Everything this project needs from JWT, and nothing it doesn't: sign a
 * token carrying the user's email (as the standard {@code sub} claim) and
 * id (as a custom claim), and later verify one came from this app and
 * hasn't expired. No refresh tokens, no revocation list, no issuer/audience
 * claims — a "minimal JWT auth" per the roadmap's own words, not a
 * full-featured auth server.
 *
 * <p>Stateless by construction: nothing about validating a token touches
 * the database or any server-side session store (the id and email inside
 * it are trusted once the signature checks out), which is what lets {@code
 * SecurityConfig} run with {@code SessionCreationPolicy.STATELESS} — no
 * session to keep in sync across app instances.</p>
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = properties.expirationMs();
    }

    public String generateToken(UserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(principal.getUsername())
                .claim("userId", principal.getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey)
                .compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Checks the token's signature, expiry, and that its subject still
     * matches the {@code UserDetails} it's being checked against — that
     * last check is what stops a token issued before an email change (were
     * this app to ever support one) from silently authenticating as the
     * new address.
     */
    public boolean isValid(String token, UserDetails userDetails) {
        String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isExpired(token);
    }

    private boolean isExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    /**
     * Every failure mode here — bad signature, malformed compact string,
     * expired token reaching a claims accessor that itself throws — comes
     * back as a {@link JwtException} (jjwt's own common superclass for all
     * of them). Callers don't need to distinguish which one occurred;
     * {@code JwtAuthenticationFilter} catches the superclass and treats
     * every case identically: leave the request unauthenticated and let it
     * fail downstream with a clean 401, rather than a 500 from an unparsed
     * token crashing the filter chain.
     */
    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }
}

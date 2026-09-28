package com.ahdyahmed.eventhub.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code security.jwt.*} from {@code application.yml}. A record, not
 * a {@code @Value}-annotated field on {@code JwtService} directly — keeping
 * the two config values together as one typed unit means a future third
 * value (an issuer claim, a refresh-token TTL) has an obvious place to go
 * without touching {@code JwtService}'s constructor signature.
 *
 * <p>{@code secret} must decode to at least 256 bits for HS256 — {@code
 * io.jsonwebtoken.security.Keys.hmacShaKeyFor} throws at startup otherwise,
 * which is the right failure mode: a JWT signing key too short to resist
 * brute-forcing should fail loudly at boot, not silently produce forgeable
 * tokens. {@code application.yml}'s default is a 44+ character local-dev
 * placeholder; the comment there is explicit about needing a real secret
 * (e.g. from an env var) in any non-local deployment.</p>
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret, long expirationMs) {
}

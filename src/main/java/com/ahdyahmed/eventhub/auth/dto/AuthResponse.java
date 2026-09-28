package com.ahdyahmed.eventhub.auth.dto;

/**
 * Returned by both {@code /register} and {@code /login} — same shape either
 * way, since a client needs the same three things (a token to send on every
 * later request, plus who it just authenticated as) regardless of which
 * endpoint got it there.
 */
public record AuthResponse(
        String token,
        Long userId,
        String fullName,
        String email
) {
}

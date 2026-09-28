package com.ahdyahmed.eventhub.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs once per request, before Spring MVC's own dispatch: reads an {@code
 * Authorization: Bearer <token>} header, and if it's present and valid,
 * populates the {@code SecurityContext} so everything downstream —
 * {@code SecurityConfig}'s {@code authorizeHttpRequests} rules, {@code
 * @AuthenticationPrincipal} in a controller — sees an authenticated
 * request.
 *
 * <p>Every failure path here — no header, malformed header, invalid or
 * expired token, a token for an email that no longer exists — does exactly
 * the same thing: leaves the {@code SecurityContext} empty and calls {@code
 * chain.doFilter} anyway, rather than rejecting the request itself. That's
 * deliberate: a request to a {@code permitAll()} endpoint (registration,
 * login, public browsing) carrying a garbage {@code Authorization} header
 * should still succeed. Whether an empty {@code SecurityContext} is
 * actually a problem is {@code SecurityConfig}'s {@code
 * authorizeHttpRequests} rule's call to make for that specific path, not
 * this filter's.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final EventHubUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            String email = jwtService.extractEmail(token);
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                if (jwtService.isValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (JwtException | UsernameNotFoundException ex) {
            // Malformed/expired/invalid-signature token, or a token whose
            // subject no longer matches any user (e.g. the account was
            // deleted after the token was issued) - see JwtService's own
            // doc for why every jjwt failure collapses to JwtException here.
            // Logged at debug, not warn: an expired token on an otherwise
            // idle client is routine, not an incident.
            log.debug("Rejected JWT: {}", ex.getMessage());
        }

        chain.doFilter(request, response);
    }
}

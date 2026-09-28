package com.ahdyahmed.eventhub.auth;

import com.ahdyahmed.eventhub.common.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * A request to a protected endpoint with no (or an invalid) token is
 * rejected by Spring Security's filter chain itself, before it ever reaches
 * a {@code @RestController} — which means it never reaches {@code
 * GlobalExceptionHandler} either, since that's a Spring MVC mechanism and
 * this rejection happens a layer below MVC entirely. Without this class,
 * that case would fall back to Spring Security's own default body (a bare
 * {@code 401} with no JSON at all), breaking the "one error shape for the
 * whole API" {@code GlobalExceptionHandler}'s own doc promises. This class
 * is what keeps that promise true for the one category of error that
 * handler structurally can't reach.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "A valid Bearer token is required for this endpoint",
                request.getRequestURI());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}

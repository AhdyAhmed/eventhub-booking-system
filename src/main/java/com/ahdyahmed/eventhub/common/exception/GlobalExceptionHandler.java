package com.ahdyahmed.eventhub.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * One place that decides how every exception type in the app becomes an
 * HTTP response, instead of that decision being scattered across
 * {@code @ResponseStatus} annotations, ad-hoc try/catch blocks in
 * controllers, or (worst case) Spring's default whitelabel error page.
 *
 * <p>Handlers are ordered roughly by how often they'll actually fire:
 * validation failures and not-found lookups are everyday client errors;
 * malformed JSON is rarer; the generic {@code Exception} handler is a safety
 * net that should, ideally, almost never be hit.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ErrorResponse body = ErrorResponse.ofValidation(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed",
                request.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(SeatUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleSeatUnavailable(SeatUnavailableException ex,
                                                                HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(BookingValidationException.class)
    public ResponseEntity<ErrorResponse> handleBookingValidation(BookingValidationException ex,
                                                                  HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * As of Day 16, genuinely reachable from an HTTP request:
     * {@code BookingServiceImpl.cancel} calls {@code
     * BookingStateMachine.transition()} directly, so a client trying to
     * cancel an already-{@code FAILED} or already-{@code CANCELLED} booking
     * hits this. {@code PaymentProcessedListener}'s Kafka-side calls to the
     * same method are unaffected by this handler — that's a consumer, not a
     * controller, and its own exceptions are handled by Day 15's {@code
     * KafkaErrorHandlingConfig} instead.
     */
    @ExceptionHandler(InvalidBookingStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBookingStateTransition(
            InvalidBookingStateTransitionException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * Day 16: {@code BookingServiceImpl.requireOwnership} throws this exact
     * exception type — Spring Security's own {@code AccessDeniedException},
     * not a project-specific one — when a caller tries to view or cancel a
     * booking that isn't theirs. Reusing Spring Security's type here, for an
     * exception thrown from plain service-layer code with no Spring
     * Security filter involved, is deliberate: "access denied" is exactly
     * what it means regardless of which layer raises it, and one handler
     * covers both this and anything else in the app that ever needs the
     * same check.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    /**
     * Day 16: {@code AuthServiceImpl.login} delegates to Spring Security's
     * {@code AuthenticationManager}, which throws a subtype of this
     * (typically {@code BadCredentialsException}) for a wrong password or
     * an email with no matching account — {@code
     * DaoAuthenticationProvider}'s default behavior deliberately doesn't
     * distinguish the two in its own exception, so this handler can't
     * either, and shouldn't try to: telling a caller "that email doesn't
     * exist" vs. "that password is wrong" would hand an attacker a way to
     * enumerate registered emails one login attempt at a time. One generic
     * message for both cases closes that off. Note this is a completely
     * separate path from {@link com.ahdyahmed.eventhub.auth.JwtAuthenticationEntryPoint} -
     * that one handles a missing/invalid *token* on an already-protected
     * endpoint, rejected by the security filter chain before reaching a
     * controller at all; this one handles a failed *login attempt* inside
     * {@code AuthServiceImpl}, a normal service-layer exception this
     * {@code @RestControllerAdvice} catches the usual way.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex,
                                                                        HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, "Invalid email or password", request);
    }

    /**
     * Defense in depth: {@code BookingServiceImpl} already catches the raw
     * optimistic-lock failure around each seat update and rethrows it as a
     * {@link SeatUnavailableException} with seat-specific context. This
     * handler exists for any future write path that touches a
     * {@code @Version}-ed entity without doing that translation itself — an
     * unhandled optimistic-lock failure should still come back as a clean
     * 409, never a raw 500.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                                               HttpServletRequest request) {
        log.warn("Unhandled optimistic lock conflict on {} {}", request.getMethod(), request.getRequestURI());
        return respond(HttpStatus.CONFLICT, "The resource was modified concurrently — please retry", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                               HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Malformed JSON request body", request);
    }

    /**
     * Catches unique/foreign-key constraint violations that reach Postgres
     * without being pre-checked in the service layer — e.g. a duplicate
     * {@code users.email}, or a duplicate {@code (event_id, seat_number)} on
     * seats (see {@code V2__domain_schema.sql}). Without this handler these
     * fell through to {@link #handleUnexpected}, which is technically
     * correct (it is an unhandled exception) but a poor experience: a
     * predictable "that value's already taken" case doesn't deserve the same
     * generic 500 as a genuine bug. The database-specific detail is logged
     * server-side only, same reasoning as the catch-all below.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                                                       HttpServletRequest request) {
        log.warn("Data integrity violation on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT,
                "Request conflicts with an existing record (e.g. a duplicate value on a unique field)", request);
    }

    /**
     * Last resort. Anything that reaches here is, by definition, a bug or an
     * unhandled failure mode — it gets logged with its stack trace server-side,
     * but the client only ever sees a generic message. Leaking internal
     * exception details (SQL, stack traces, class names) in an API response
     * is an information-disclosure risk, not just an ugly response.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }

}

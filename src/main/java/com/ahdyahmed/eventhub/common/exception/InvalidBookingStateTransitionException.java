package com.ahdyahmed.eventhub.common.exception;

import com.ahdyahmed.eventhub.booking.BookingStatus;

/**
 * Thrown by {@link com.ahdyahmed.eventhub.booking.BookingStateMachine} when
 * something asks for a transition its transition table doesn't allow —
 * e.g. a redelivered, out-of-order Kafka message trying to move an already
 * {@code CONFIRMED} booking to {@code FAILED}.
 *
 * <p>Mapped to {@code 409 Conflict} in {@link GlobalExceptionHandler} even
 * though nothing on the HTTP side throws it yet as of Day 14 — {@code
 * PaymentProcessedListener} is a Kafka consumer, not a controller, so an
 * uncaught instance here is just logged by Spring Kafka's default error
 * handler for now (Day 15 adds the retry/DLT policy around that). The
 * mapping is here regardless because Day 16's planned booking-cancellation
 * endpoint will call the same {@code transition()} method from an actual
 * HTTP request path, and this exception shouldn't need to change - or be
 * remembered - when that happens.</p>
 */
public class InvalidBookingStateTransitionException extends RuntimeException {

    public InvalidBookingStateTransitionException(Long bookingId, BookingStatus from, BookingStatus to) {
        super("Booking %d cannot transition from %s to %s".formatted(bookingId, from, to));
    }
}

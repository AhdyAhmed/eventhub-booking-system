package com.ahdyahmed.eventhub.common.exception;

import com.ahdyahmed.eventhub.booking.BookingStatus;

/**
 * Thrown by {@link com.ahdyahmed.eventhub.booking.BookingStateMachine} when
 * something asks for a transition its transition table doesn't allow —
 * e.g. a redelivered, out-of-order Kafka message trying to move an already
 * {@code CONFIRMED} booking to {@code FAILED}.
 *
 * <p>Mapped to {@code 409 Conflict} in {@link GlobalExceptionHandler}. Two
 * genuinely different callers can trigger it: {@code
 * PaymentProcessedListener} (a Kafka consumer — an uncaught instance there
 * is actually handled by Day 15's {@code KafkaErrorHandlingConfig} instead
 * of this mapping, since it's registered as non-retryable on that shared
 * error handler and goes straight to the dead-letter topic without a
 * pointless backoff delay first), and, as of Day 16, {@code
 * BookingServiceImpl.cancel} — a real HTTP request path, where this 409
 * mapping is what a client actually sees for e.g. trying to cancel an
 * already-{@code FAILED} booking. The mapping was written before that
 * second caller existed specifically so this exception wouldn't need to
 * change - or be remembered - once it did.</p>
 */
public class InvalidBookingStateTransitionException extends RuntimeException {

    public InvalidBookingStateTransitionException(Long bookingId, BookingStatus from, BookingStatus to) {
        super("Booking %d cannot transition from %s to %s".formatted(bookingId, from, to));
    }
}

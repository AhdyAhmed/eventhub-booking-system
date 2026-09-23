package com.ahdyahmed.eventhub.common.exception;

/**
 * Thrown when a seat can't be reserved — either because a pre-check found it
 * already isn't {@code AVAILABLE}, or because a concurrent booking won the
 * race and an optimistic-lock failure surfaced instead. Both cases mean the
 * same thing to the client: this seat is gone, try a different one. Mapped
 * to 409 Conflict by {@link GlobalExceptionHandler} — a 404 would be wrong
 * (the seat exists), and a 400 would be wrong (the request itself was
 * perfectly valid when it was made).
 */
public class SeatUnavailableException extends RuntimeException {

    public SeatUnavailableException(Long seatId) {
        super("Seat " + seatId + " is not available for booking");
    }

}

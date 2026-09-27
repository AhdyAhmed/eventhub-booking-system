package com.ahdyahmed.eventhub.common.exception;

/**
 * For booking business rules that span multiple fields or require a lookup
 * to evaluate (e.g. "all seats must belong to the same event") — the kind of
 * check bean validation's per-field annotations can't express, but which is
 * still a client error, not a server one. Mapped to 400 Bad Request.
 */
public class BookingValidationException extends RuntimeException {

    public BookingValidationException(String message) {
        super(message);
    }

}

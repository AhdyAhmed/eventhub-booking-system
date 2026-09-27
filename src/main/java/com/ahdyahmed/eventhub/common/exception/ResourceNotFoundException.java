package com.ahdyahmed.eventhub.common.exception;

/**
 * Thrown when a lookup by id finds nothing. Mapped to a 404 by {@link
 * GlobalExceptionHandler} — previously this carried a {@code
 * @ResponseStatus(404)} annotation directly as a Day 3 stopgap; now that
 * there's a real global handler, status mapping happens in exactly one
 * place instead of two.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

}

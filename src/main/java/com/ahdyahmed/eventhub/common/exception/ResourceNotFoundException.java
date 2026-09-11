package com.ahdyahmed.eventhub.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a lookup by id finds nothing.
 *
 * <p>{@code @ResponseStatus} is a deliberate stopgap: it gets a correct 404
 * out the door today without building the full error-handling infrastructure
 * early. Day 5 replaces this with a {@code @ControllerAdvice} that gives
 * every exception type (this one included) a consistent JSON error body
 * instead of Spring's default error page shape.</p>
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

}

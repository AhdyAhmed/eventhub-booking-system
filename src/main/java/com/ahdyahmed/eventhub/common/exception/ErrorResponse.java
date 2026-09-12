package com.ahdyahmed.eventhub.common.exception;

import java.time.Instant;
import java.util.Map;

/**
 * The one error shape every exception in the API maps to.
 *
 * <p>{@code fieldErrors} is null for anything that isn't a validation
 * failure — keeping a single response type (rather than a separate one for
 * validation errors) means clients only ever need to handle one JSON shape,
 * with one field that's sometimes absent.</p>
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null);
    }

    public static ErrorResponse ofValidation(int status, String error, String message, String path,
                                              Map<String, String> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, error, message, path, fieldErrors);
    }

}

package com.ahdyahmed.eventhub.common.exception;

import com.ahdyahmed.eventhub.common.logging.MdcKeys;
import java.time.Instant;
import java.util.Map;
import org.slf4j.MDC;

/**
 * The one error shape every exception in the API maps to.
 *
 * <p>{@code fieldErrors} is null for anything that isn't a validation
 * failure — keeping a single response type (rather than a separate one for
 * validation errors) means clients only ever need to handle one JSON shape,
 * with one field that's sometimes absent.</p>
 *
 * <p>{@code correlationId} (Day 17) is the same id the {@code
 * X-Correlation-Id} response header carries and every log line for this
 * request is stamped with — putting it in the body too means a client that
 * only surfaces the error JSON (a mobile app's error screen, a pasted
 * bug report) still hands support the one string that finds the request in
 * the logs. It's read from the MDC inside the factory methods, not passed
 * in, so no call site (or future handler) can forget it. Outside a request
 * (nothing set it) it's {@code null}.</p>
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        Map<String, String> fieldErrors
) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, currentCorrelationId(), null);
    }

    public static ErrorResponse ofValidation(int status, String error, String message, String path,
                                              Map<String, String> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, error, message, path, currentCorrelationId(), fieldErrors);
    }

    private static String currentCorrelationId() {
        return MDC.get(MdcKeys.CORRELATION_ID);
    }

}

package com.ahdyahmed.eventhub.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.ahdyahmed.eventhub.common.logging.MdcKeys;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class ErrorResponseTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void of_carriesTheCurrentRequestsCorrelationId() {
        MDC.put(MdcKeys.CORRELATION_ID, "corr-404");

        ErrorResponse response = ErrorResponse.of(404, "Not Found", "no such booking", "/api/v1/bookings/9");

        assertThat(response.correlationId()).isEqualTo("corr-404");
        assertThat(response.fieldErrors()).isNull();
    }

    @Test
    void ofValidation_carriesItToo() {
        MDC.put(MdcKeys.CORRELATION_ID, "corr-400");

        ErrorResponse response = ErrorResponse.ofValidation(
                400, "Bad Request", "Validation failed", "/api/v1/events", Map.of("name", "must not be blank"));

        assertThat(response.correlationId()).isEqualTo("corr-400");
        assertThat(response.fieldErrors()).containsEntry("name", "must not be blank");
    }

    @Test
    void outsideARequest_correlationIdIsNull_notAnEmptyString() {
        ErrorResponse response = ErrorResponse.of(500, "Internal Server Error", "boom", "/x");

        assertThat(response.correlationId()).isNull();
    }
}

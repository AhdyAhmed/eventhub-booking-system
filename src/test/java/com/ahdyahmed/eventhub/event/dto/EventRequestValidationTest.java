package com.ahdyahmed.eventhub.event.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Exercises validation directly through {@link Validator}, with no Spring
 * context involved — this is exactly the same validator Spring Boot wires
 * up via {@code @Valid}, so it's a fast, accurate way to unit test both the
 * built-in constraints and the two custom ones ({@code @FutureByHours},
 * {@code @ValidCategory}) in isolation from any HTTP or database concern.
 */
class EventRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    private EventRequest validRequest() {
        return new EventRequest(1L, "Launch Night", "desc", "CONCERT", Instant.now().plus(2, ChronoUnit.HOURS));
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    void blankName_isRejected() {
        EventRequest request = new EventRequest(1L, "", "desc", "CONCERT", Instant.now().plus(2, ChronoUnit.HOURS));

        Set<ConstraintViolation<EventRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    void missingVenueId_isRejected() {
        EventRequest request = new EventRequest(null, "Name", "desc", "CONCERT", Instant.now().plus(2, ChronoUnit.HOURS));

        Set<ConstraintViolation<EventRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("venueId"));
    }

    @Test
    void unknownCategory_isRejectedByCustomValidator() {
        EventRequest request = new EventRequest(1L, "Name", "desc", "NOT_A_REAL_CATEGORY",
                Instant.now().plus(2, ChronoUnit.HOURS));

        Set<ConstraintViolation<EventRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("category"));
    }

    @Test
    void categoryIsCaseInsensitive() {
        EventRequest request = new EventRequest(1L, "Name", "desc", "concert",
                Instant.now().plus(2, ChronoUnit.HOURS));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void eventDateLessThanOneHourAway_isRejectedByCustomValidator() {
        EventRequest request = new EventRequest(1L, "Name", "desc", "CONCERT",
                Instant.now().plus(10, ChronoUnit.MINUTES));

        Set<ConstraintViolation<EventRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("eventDate"));
    }

    @Test
    void eventDateInThePast_isRejected() {
        EventRequest request = new EventRequest(1L, "Name", "desc", "CONCERT",
                Instant.now().minus(1, ChronoUnit.DAYS));

        Set<ConstraintViolation<EventRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("eventDate"));
    }

}

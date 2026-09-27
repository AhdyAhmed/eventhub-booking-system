package com.ahdyahmed.eventhub.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that an {@code Instant} is at least {@link #hours()} hours from
 * now. Deliberately its own constraint rather than the built-in
 * {@code @Future} — "in the future" isn't a strict enough business rule for
 * an event booking system; there needs to be enough lead time for seat
 * inventory, pricing, and notifications to be set up before anyone can book.
 *
 * <p>Not tied to the {@code event} package on purpose — any future feature
 * with the same "needs N hours of lead time" shape (e.g. a cancellation
 * cutoff) can reuse this.</p>
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FutureByHoursValidator.class)
public @interface FutureByHours {

    String message() default "must be at least {hours} hour(s) from now";

    int hours() default 1;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}

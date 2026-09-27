package com.ahdyahmed.eventhub.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class FutureByHoursValidator implements ConstraintValidator<FutureByHours, Instant> {

    private int hours;

    @Override
    public void initialize(FutureByHours constraintAnnotation) {
        this.hours = constraintAnnotation.hours();
    }

    @Override
    public boolean isValid(Instant value, ConstraintValidatorContext context) {
        // Null is a presence concern, not a "when" concern — leave it to
        // @NotNull so each constraint has exactly one job.
        if (value == null) {
            return true;
        }
        return value.isAfter(Instant.now().plus(hours, ChronoUnit.HOURS));
    }

}

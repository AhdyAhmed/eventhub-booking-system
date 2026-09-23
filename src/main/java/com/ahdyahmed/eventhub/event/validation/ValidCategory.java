package com.ahdyahmed.eventhub.event.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts an Event's category to a known set of values. A plain
 * {@code @Pattern} regex could technically do this, but a dedicated
 * constraint keeps the allowed-values list in one place ({@link
 * ValidCategoryValidator}) instead of duplicated across every regex that
 * needs it, and gives a clearer validation message than a regex failure
 * would.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidCategoryValidator.class)
public @interface ValidCategory {

    String message() default "must be one of: CONCERT, SPORTS, THEATER, CONFERENCE, EXHIBITION, OTHER";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}

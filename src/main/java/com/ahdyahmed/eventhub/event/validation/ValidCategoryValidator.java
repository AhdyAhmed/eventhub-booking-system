package com.ahdyahmed.eventhub.event.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

public class ValidCategoryValidator implements ConstraintValidator<ValidCategory, String> {

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "CONCERT", "SPORTS", "THEATER", "CONFERENCE", "EXHIBITION", "OTHER"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Blank/null is a presence concern, handled by @NotBlank separately.
        if (value == null || value.isBlank()) {
            return true;
        }
        return ALLOWED_CATEGORIES.contains(value.toUpperCase());
    }

}

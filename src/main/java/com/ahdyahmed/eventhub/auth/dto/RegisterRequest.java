package com.ahdyahmed.eventhub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "New account details; registration returns a ready-to-use JWT")
public record RegisterRequest(
        @Schema(example = "Demo Recruiter")
        @NotBlank(message = "fullName is required")
        String fullName,

        @Schema(example = "demo@example.com")
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        String email,

        @Schema(example = "correct-horse-battery", minLength = 8)
        @NotBlank(message = "password is required")
        @Size(min = 8, message = "password must be at least 8 characters")
        String password
) {
}

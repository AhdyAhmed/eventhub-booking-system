package com.ahdyahmed.eventhub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credentials for an existing EventHub account")
public record LoginRequest(
        @Schema(example = "demo@example.com")
        @NotBlank(message = "email is required")
        String email,

        @Schema(example = "correct-horse-battery")
        @NotBlank(message = "password is required")
        String password
) {
}

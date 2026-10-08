package com.ahdyahmed.eventhub.venue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a Venue.
 */
@Schema(description = "Venue details used by create and update operations")
public record VenueRequest(
        @Schema(example = "Cairo Arena")
        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        @Schema(example = "Cairo")
        @NotBlank(message = "city is required")
        @Size(max = 100, message = "city must be at most 100 characters")
        String city,

        @Schema(example = "Nasr City, Cairo")
        @Size(max = 255, message = "address must be at most 255 characters")
        String address,

        @Schema(example = "5000")
        @NotNull(message = "capacity is required")
        @Positive(message = "capacity must be a positive number")
        Integer capacity
) {
}

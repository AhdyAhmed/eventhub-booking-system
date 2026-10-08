package com.ahdyahmed.eventhub.event.dto;

import com.ahdyahmed.eventhub.common.validation.FutureByHours;
import com.ahdyahmed.eventhub.event.validation.ValidCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Request body for creating or updating an Event.
 *
 * <p>{@code venueId} rather than a nested venue object — the client refers
 * to an existing Venue by id, it doesn't get to create/edit one inline
 * through the Event endpoint.</p>
 */
@Schema(description = "Create or replace an event; eventDate must be at least one hour in the future")
public record EventRequest(
        @Schema(example = "1")
        @NotNull(message = "venueId is required")
        Long venueId,

        @Schema(example = "Cairo Tech Summit")
        @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        @Schema(example = "A full-day backend engineering conference")
        String description,

        @Schema(example = "CONFERENCE", allowableValues = {"CONCERT", "SPORTS", "THEATER", "CONFERENCE", "EXHIBITION", "OTHER"})
        @NotBlank(message = "category is required")
        @ValidCategory
        String category,

        @Schema(example = "2030-12-01T19:00:00Z", type = "string", format = "date-time")
        @NotNull(message = "eventDate is required")
        @FutureByHours(hours = 1, message = "eventDate must be at least 1 hour from now")
        Instant eventDate
) {
}

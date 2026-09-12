package com.ahdyahmed.eventhub.event.dto;

import com.ahdyahmed.eventhub.common.validation.FutureByHours;
import com.ahdyahmed.eventhub.event.validation.ValidCategory;
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
public record EventRequest(
        @NotNull(message = "venueId is required")
        Long venueId,

        @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        String description,

        @NotBlank(message = "category is required")
        @ValidCategory
        String category,

        @NotNull(message = "eventDate is required")
        @FutureByHours(hours = 1, message = "eventDate must be at least 1 hour from now")
        Instant eventDate
) {
}

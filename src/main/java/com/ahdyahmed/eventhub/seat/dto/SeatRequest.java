package com.ahdyahmed.eventhub.seat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Seats are always created under a specific event
 * ({@code POST /api/v1/events/{eventId}/seats}), so there's no
 * {@code eventId} field here — it comes from the path, not the body.
 */
@Schema(description = "A seat within the event identified by the request path")
public record SeatRequest(
        @Schema(example = "A1")
        @NotBlank(message = "seatNumber is required")
        String seatNumber,

        @Schema(example = "Main Hall")
        String section,

        @Schema(example = "120.00")
        @NotNull(message = "price is required")
        @Positive(message = "price must be a positive amount")
        BigDecimal price
) {
}

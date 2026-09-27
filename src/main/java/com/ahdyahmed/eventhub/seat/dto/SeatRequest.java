package com.ahdyahmed.eventhub.seat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Seats are always created under a specific event
 * ({@code POST /api/v1/events/{eventId}/seats}), so there's no
 * {@code eventId} field here — it comes from the path, not the body.
 */
public record SeatRequest(
        @NotBlank(message = "seatNumber is required")
        String seatNumber,

        String section,

        @NotNull(message = "price is required")
        @Positive(message = "price must be a positive amount")
        BigDecimal price
) {
}

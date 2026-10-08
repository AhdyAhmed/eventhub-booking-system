package com.ahdyahmed.eventhub.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * {@code userId} lived here through Day 15, with this class's own doc
 * predicting exactly this change once auth existed: trusting a
 * client-supplied id for "who is this booking for" would let any caller
 * book seats in someone else's name simply by putting a different number in
 * the request body. As of Day 16, {@code BookingController} reads the
 * booking owner from the authenticated {@code UserPrincipal} instead and
 * passes it to {@code BookingService.create} as an explicit parameter - not
 * something a request body field, trusted or not, needs to carry anymore.
 */
@Schema(description = "Seat IDs to reserve atomically for the authenticated user")
public record BookingRequest(
        @Schema(example = "[1, 2]")
        @NotEmpty(message = "seatIds must contain at least one seat")
        List<@NotNull(message = "seatIds must not contain null values") Long> seatIds
) {
}

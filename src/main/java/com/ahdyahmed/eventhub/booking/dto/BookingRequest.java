package com.ahdyahmed.eventhub.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * {@code userId} is passed explicitly here rather than taken from a security
 * context — there's no auth yet (that's Day 16). Once JWT is wired in, this
 * will almost certainly change to read the current user from the
 * authenticated principal instead of trusting a client-supplied id.
 */
public record BookingRequest(
        @NotNull(message = "userId is required")
        Long userId,

        @NotEmpty(message = "seatIds must contain at least one seat")
        List<Long> seatIds
) {
}

package com.ahdyahmed.eventhub.booking.dto;

import java.math.BigDecimal;

public record BookingItemResponse(
        Long seatId,
        String seatNumber,
        BigDecimal priceAtBooking
) {
}

package com.ahdyahmed.eventhub.booking.dto;

import com.ahdyahmed.eventhub.booking.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingResponse(
        Long id,
        Long userId,
        BookingStatus status,
        BigDecimal totalAmount,
        List<BookingItemResponse> items,
        Instant createdAt
) {
}

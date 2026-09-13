package com.ahdyahmed.eventhub.seat.dto;

import com.ahdyahmed.eventhub.seat.SeatStatus;
import java.math.BigDecimal;

public record SeatResponse(
        Long id,
        Long eventId,
        String seatNumber,
        String section,
        BigDecimal price,
        SeatStatus status
) {
}

package com.ahdyahmed.eventhub.event.dto;

import java.time.Instant;

public record EventResponse(
        Long id,
        VenueSummary venue,
        String name,
        String description,
        String category,
        Instant eventDate,
        Instant createdAt,
        Instant updatedAt
) {
}

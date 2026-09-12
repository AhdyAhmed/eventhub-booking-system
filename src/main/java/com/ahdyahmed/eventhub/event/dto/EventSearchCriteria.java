package com.ahdyahmed.eventhub.event.dto;

import java.time.Instant;

/**
 * Every field is optional — this is the input to a dynamic filter, not a
 * strict query contract. An all-null instance means "no filtering, just
 * paginate everything."
 */
public record EventSearchCriteria(
        Long venueId,
        String city,
        String category,
        Instant fromDate,
        Instant toDate
) {
}

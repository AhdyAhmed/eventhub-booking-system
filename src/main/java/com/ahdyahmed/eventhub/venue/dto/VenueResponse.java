package com.ahdyahmed.eventhub.venue.dto;

import java.time.Instant;

/**
 * What a Venue looks like over the wire. Deliberately doesn't include the
 * list of events — fetching an entity's full graph on every read is how you
 * end up with an accidental N+1 or a huge payload; callers that want a
 * venue's events use {@code GET /api/v1/events?venueId=...} instead (Day 4).
 */
public record VenueResponse(
        Long id,
        String name,
        String city,
        String address,
        Integer capacity,
        Instant createdAt,
        Instant updatedAt
) {
}

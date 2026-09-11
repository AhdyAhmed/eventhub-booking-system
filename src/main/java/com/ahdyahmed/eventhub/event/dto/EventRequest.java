package com.ahdyahmed.eventhub.event.dto;

import java.time.Instant;

/**
 * Request body for creating or updating an Event.
 *
 * <p>{@code venueId} rather than a nested venue object — the client refers
 * to an existing Venue by id, it doesn't get to create/edit one inline
 * through the Event endpoint. No validation annotations yet; that's Day 5.</p>
 */
public record EventRequest(
        Long venueId,
        String name,
        String description,
        String category,
        Instant eventDate
) {
}

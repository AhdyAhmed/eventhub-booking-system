package com.ahdyahmed.eventhub.event.dto;

/**
 * Just enough about a Venue for an Event response to be useful without a
 * second round trip. Deliberately its own small type local to the {@code
 * event} package rather than reusing {@code venue.dto.VenueResponse} — each
 * feature package depends only on what it actually needs, not on another
 * feature's full response shape.
 */
public record VenueSummary(
        Long id,
        String name,
        String city
) {
}

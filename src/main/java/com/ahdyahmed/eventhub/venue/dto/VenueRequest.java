package com.ahdyahmed.eventhub.venue.dto;

/**
 * Request body for creating or updating a Venue.
 *
 * <p>No validation annotations yet — bean validation (and what happens when
 * it fails) is Day 5's job. Right now this only exists so the controller
 * never accepts or returns a JPA entity directly.</p>
 */
public record VenueRequest(
        String name,
        String city,
        String address,
        Integer capacity
) {
}

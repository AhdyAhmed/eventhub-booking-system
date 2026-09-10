package com.ahdyahmed.eventhub.seat;

/**
 * Lifecycle of a single seat's availability for its event.
 *
 * <p>RESERVED is a short-lived state for the window between "user selected
 * this seat" and "payment confirmed" (introduced properly on Day 6/14) —
 * it exists in the model now so the schema doesn't need to change later.</p>
 */
public enum SeatStatus {
    AVAILABLE,
    RESERVED,
    BOOKED
}

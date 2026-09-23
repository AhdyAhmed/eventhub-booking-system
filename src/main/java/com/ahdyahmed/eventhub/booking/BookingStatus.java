package com.ahdyahmed.eventhub.booking;

/**
 * Booking lifecycle. The full state machine (which transitions are legal,
 * who can trigger them) is formalized on Day 14 alongside the mock payment
 * step — this enum just declares the states up front so the schema is
 * settled from Day 2 onward.
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    FAILED
}

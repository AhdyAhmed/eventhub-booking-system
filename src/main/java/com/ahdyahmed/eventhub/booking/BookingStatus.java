package com.ahdyahmed.eventhub.booking;

/**
 * Booking lifecycle. Declared in full since Day 2 so the schema never had
 * to change shape later; the legal transitions between these states are
 * formalized as of Day 14 in {@link BookingStateMachine}, driven by {@link
 * PaymentProcessedListener} reacting to the mock payment step.
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    FAILED
}

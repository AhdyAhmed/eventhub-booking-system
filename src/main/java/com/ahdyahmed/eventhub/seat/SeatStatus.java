package com.ahdyahmed.eventhub.seat;

/**
 * Lifecycle of a single seat's availability for its event.
 *
 * <p>RESERVED is the short-lived state for the window between "user
 * selected this seat" (Day 6, {@code BookingServiceImpl.reserve}) and
 * "payment resolved" (Day 14, {@code PaymentProcessedListener}) — a
 * successful mock charge moves it on to BOOKED; a declined one moves it
 * back to AVAILABLE for someone else to book, rather than leaving it
 * stuck.</p>
 */
public enum SeatStatus {
    AVAILABLE,
    RESERVED,
    BOOKED
}

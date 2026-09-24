package com.ahdyahmed.eventhub.payment;

/**
 * Outcome of a single mock payment attempt (see {@link MockPaymentServiceImpl}).
 *
 * <p>Deliberately two states, not three: there is no {@code PENDING} here.
 * {@link PaymentConsumer} calls {@link PaymentService#charge} synchronously
 * and gets one of these back immediately — a real payment gateway's own
 * "processing, we'll webhook you" state is exactly the kind of complexity
 * this mock isn't trying to simulate. {@code Booking}'s own
 * {@link com.ahdyahmed.eventhub.booking.BookingStatus#PENDING} already
 * covers "payment not resolved yet" at the booking level, which is the
 * only place that state actually needs to be visible.</p>
 */
public enum PaymentStatus {
    SUCCEEDED,
    FAILED
}

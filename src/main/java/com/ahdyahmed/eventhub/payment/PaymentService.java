package com.ahdyahmed.eventhub.payment;

import com.ahdyahmed.eventhub.booking.event.BookingConfirmedEvent;

/**
 * The mock payment step the roadmap calls for on Day 14. An interface
 * exists at all — rather than {@link PaymentConsumer} calling a concrete
 * mock class directly — for the same reason {@code BookingService} sits in
 * front of {@code BookingServiceImpl}: swapping the mock for a real gateway
 * integration later is a new implementation of this contract, not a rewrite
 * of the Kafka wiring around it.
 */
public interface PaymentService {

    /**
     * Attempts to charge for a confirmed booking. Never throws for a
     * business-level decline — a decline is a normal, expected outcome
     * ({@link PaymentResult#failure}), not an exceptional one. An actual
     * thrown exception here is reserved for something genuinely
     * unexpected, and — matching {@link com.ahdyahmed.eventhub.notification.NotificationListener}'s
     * accepted Day-13 gap — isn't retried yet; that's Day 15.
     */
    PaymentResult charge(BookingConfirmedEvent booking);
}

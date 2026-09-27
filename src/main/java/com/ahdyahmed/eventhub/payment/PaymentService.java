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
     * unexpected — and as of Day 15, an uncaught one propagating out of
     * {@link PaymentConsumer#onBookingConfirmed} is retried with
     * exponential backoff and then dead-lettered by the shared policy in
     * {@code KafkaErrorHandlingConfig}, the same as any other uncaught
     * exception from a {@code @KafkaListener} method on the default
     * container factory.
     */
    PaymentResult charge(BookingConfirmedEvent booking);
}
